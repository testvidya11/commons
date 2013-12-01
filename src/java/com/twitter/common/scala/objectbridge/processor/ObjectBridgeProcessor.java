// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================
package com.twitter.common.scala.objectbridge.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import com.twitter.common.scala.objectbridge.annotations.BridgeDefinitions;

/**
 * XXX
 */
public class ObjectBridgeProcessor extends AbstractProcessor {

  private Elements elementUtils;
  private Types typeUtils;
  private Filer filer;
  private Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    elementUtils = processingEnv.getElementUtils();
    typeUtils = processingEnv.getTypeUtils();
    filer = processingEnv.getFiler();
    messager = processingEnv.getMessager();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(BridgeDefinitions.class.getName());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : getAnnotatedElements(roundEnv, BridgeDefinitions.class)) {
      if (!(element instanceof PackageElement)) {
        error("@BridgeDefinitions can only annotate packages, found %s", element);
      }
      PackageElement packageElement = (PackageElement) element;
      try {
        process(packageElement);
      } catch (IOException e) {
        error("Problem processing @BridgeDefinitions annotated element %s: %s", element, e);
      } catch (ClassNotFoundException e) {
        error("Problem processing @BridgeDefinitions annotated element %s: %s", element, e);
      }
    }
    return true;
  }

  private void process(PackageElement packageElement) throws ClassNotFoundException, IOException {
    AnnotationMirror bridgeDefinitions =
        getAnnotationMirror(packageElement, typeElement(BridgeDefinitions.class));

    for (AnnotationMirror objectBridge : getAnnotationArrayValue(bridgeDefinitions, "value")) {
      process(packageElement, objectBridge);
    }
  }

  private void process(PackageElement packageElement, AnnotationMirror objectBridge)
      throws ClassNotFoundException, IOException {

    TypeElement objectType = getClassValue(objectBridge, "object", null);
    if (objectType == null) {
      error("");
    }

    String bridge = getStringValue(objectBridge, "bridge", null);
    String bridgeClassName = packageElement.getQualifiedName().toString() + '.' + bridge;
    try {
      Class.forName(bridgeClassName);
      error("");
    } catch (ClassNotFoundException e) {
      // expected
    }

    System.out.println(">>> " + objectType);
    Class<?> objectClass = Class.forName(objectType.getQualifiedName().toString());
    log(Kind.NOTE, "Creating forwarder for %s in %s", objectClass, bridgeClassName);
    byte [] contents = new ObjectForwarder(objectClass).create(bridgeClassName);

    JavaFileObject classFile = filer.createClassFile(bridgeClassName, packageElement);
    Closer closer = Closer.create();
    OutputStream output = closer.register(classFile.openOutputStream());
    try {
      ByteStreams.asByteSource(contents).copyTo(output);
    } catch (IOException e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }

    log(Kind.NOTE, "Created in %s", classFile.getName());
  }

  private Set<? extends Element> getAnnotatedElements(RoundEnvironment roundEnv,
      Class<? extends Annotation> argAnnotation) {
    return roundEnv.getElementsAnnotatedWith(typeElement(argAnnotation));
  }

  @Nullable
  private AnnotationMirror getAnnotationMirror(Element element, TypeElement annotationType) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (typeUtils.isSameType(annotationMirror.getAnnotationType(), annotationType.asType())) {
        return annotationMirror;
      }
    }
    error("Failed to find an annotation of type %s on %s", annotationType, element);
    return null;
  }

  private List<AnnotationMirror> getAnnotationArrayValue(
      AnnotationMirror annotationMirror,
      String methodName) {

    List<? extends AnnotationValue> annotationValues = getAnnotationValue(
        annotationMirror,
        methodName,
        new SimpleAnnotationValueVisitor6<List<? extends AnnotationValue>, Void>() {
          @Override
          public List<? extends AnnotationValue> visitArray(
              List<? extends AnnotationValue> vals, Void unused) {

            return vals;
          }
        }, null);
    if (annotationValues != null) {
      List<AnnotationMirror> annotations = Lists.newArrayListWithCapacity(annotationValues.size());
      for (AnnotationValue annotationValue : annotationValues) {
        AnnotationMirror annotation = annotationValue.accept(
            new SimpleAnnotationValueVisitor6<AnnotationMirror, Void>() {
              @Override public AnnotationMirror visitAnnotation(
                  AnnotationMirror a, Void unused) {
                return a;
              }
            }, null);
        if (annotation == null) {
          error("");
        }
        annotations.add(annotation);
      }
      return annotations;
    }
    error("Could not find a class type for %s.%s", annotationMirror, methodName);
    return null; // Can't happen
  }

  private TypeElement getClassValue(
      AnnotationMirror annotationMirror,
      String methodName,
      TypeElement defaultClassType) {

    return getAnnotationValue(
        annotationMirror,
        methodName,
        new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
          @Override public TypeElement visitType(TypeMirror t, Void unused) {
            return (TypeElement) typeUtils.asElement(t);
          }
        },
        defaultClassType);
  }

  private String getStringValue(
      AnnotationMirror annotationMirror,
      String methodName,
      String defaultValue) {

    return getAnnotationValue(
        annotationMirror,
        methodName,
        new SimpleAnnotationValueVisitor6<String, Void>() {
          @Override public String visitString(String value, Void unused) {
            return value;
          }
        },
        defaultValue);
  }

  private <T> T getAnnotationValue(
      AnnotationMirror annotationMirror,
      String methodName,
      AnnotationValueVisitor<T, Void> valueVisitor,
      T defaultValue) {

    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : annotationMirror.getElementValues().entrySet()) {

      if (entry.getKey().getSimpleName().equals(elementUtils.getName(methodName))) {
        T value = entry.getValue().accept(valueVisitor, null);
        if (value != null) {
          return value;
        }
      }
    }
    if (defaultValue == null) {
      error("Could not find a value for %s.%s", annotationMirror, methodName);
    }
    return defaultValue;
  }

  private TypeElement typeElement(Class<?> type) {
    return elementUtils.getTypeElement(type.getName());
  }

  private void error(String message, Object ... args) {
    log(Kind.ERROR, message, args);
  }

  private void log(Kind kind, String message, Object ... args) {
    messager.printMessage(kind, String.format(message, args));
  }
}
