package com.twitter.common.scala.objectbridge.processor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.google.common.base.Preconditions;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class ObjectForwarder {
  private final Class<?> objectClass;
  private final ClassWriter classWriter;

  ObjectForwarder(Class<?> objectClass) {
    this.objectClass = Preconditions.checkNotNull(objectClass);
    classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
  }

  byte[] create(String className) {
    classWriter.visit(
        Opcodes.V1_5,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
        className.replace('.', '/'),
        null,
        Type.getInternalName(Object.class),
        null);
    doConstructor();
    doForwards();
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private void doConstructor() {
    MethodVisitor methodVisitor =
        classWriter.visitMethod(
            Opcodes.ACC_PRIVATE,
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE),
            null,
            null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        Type.getInternalName(Object.class),
        "<init>",
        Type.getMethodDescriptor(Type.VOID_TYPE));
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();
  }

  private void doForwards() {
    for (Method method : objectClass.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()) {
        forward(method);
      }
    }
  }

  private void forward(Method method) {
    MethodVisitor methodVisitor =
        classWriter.visitMethod(
            method.getModifiers() + Modifier.STATIC,
            method.getName(),
            Type.getMethodDescriptor(method),
            null, // TODO(John Sirois): Handle signatures
            asInternalNames(method.getExceptionTypes()));

    methodVisitor.visitCode();
    methodVisitor.visitFieldInsn(
        Opcodes.GETSTATIC,
        Type.getInternalName(method.getDeclaringClass()),
        "MODULE$",
        Type.getDescriptor(method.getDeclaringClass()));

    Class<?>[] parameterTypes = method.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
      methodVisitor.visitVarInsn(getLoadOpcode(parameterTypes[i]), i);
    }

    methodVisitor.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        Type.getInternalName(method.getDeclaringClass()),
        method.getName(),
        Type.getMethodDescriptor(method));

    methodVisitor.visitInsn(getReturnOpcode(Type.getReturnType(method)));

    methodVisitor.visitMaxs(parameterTypes.length + 1, parameterTypes.length);
    methodVisitor.visitEnd();
  }

  private String[] asInternalNames(Class<?>[] classes) {
    String[] internalNames = new String[classes.length];
    for (int i = 0; i < classes.length; i++) {
      internalNames[i] = Type.getInternalName(classes[i]);
    }
    return internalNames;
  }

  private int getReturnOpcode(Type returnType) {
    switch (returnType.getSort()) {
      case Type.VOID:
        return Opcodes.RETURN;
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
        return Opcodes.IRETURN;
      case Type.LONG:
        return Opcodes.LRETURN;
      case Type.FLOAT:
        return Opcodes.FRETURN;
      case Type.DOUBLE:
        return Opcodes.DRETURN;
      case Type.ARRAY:
      case Type.OBJECT:
        return Opcodes.ARETURN;
      default:
        throw new IllegalArgumentException("Unknown return type: " + returnType);
    }
  }

  private int getLoadOpcode(Class<?> parameterType) {
    switch (Type.getType(parameterType).getSort()) {
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
        return Opcodes.ILOAD;
      case Type.LONG:
        return Opcodes.LLOAD;
      case Type.FLOAT:
        return Opcodes.FLOAD;
      case Type.DOUBLE:
        return Opcodes.DLOAD;
      case Type.ARRAY:
        return getArrayLoadOpcode(parameterType.getComponentType());
      case Type.OBJECT:
        return Opcodes.ALOAD;
      default:
        throw new IllegalArgumentException("Unknown parameter type: " + parameterType);
    }
  }

  private int getArrayLoadOpcode(Class<?> componentType) {
    switch (Type.getType(componentType).getSort()) {
      case Type.BOOLEAN:
      case Type.BYTE:
        return Opcodes.BALOAD;
      case Type.CHAR:
        return Opcodes.CALOAD;
      case Type.SHORT:
        return Opcodes.SALOAD;
      case Type.INT:
        return Opcodes.IALOAD;
      case Type.LONG:
        return Opcodes.LALOAD;
      case Type.FLOAT:
        return Opcodes.FALOAD;
      case Type.DOUBLE:
        return Opcodes.DALOAD;
      case Type.ARRAY:
      case Type.OBJECT:
        return Opcodes.AALOAD;
      default:
        throw new IllegalArgumentException("Unknown array component type: " + componentType);
    }
  }
}
