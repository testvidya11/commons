annotation_processor(name='processor',
  sources=['ObjectBridgeProcessor.java', 'ObjectForwarder.java'],
  dependencies=[
    jar('org.ow2.asm', 'asm', '4.2').with_sources(),
    '3rdparty:guava',
    'src/java/com/twitter/common/scala/objectbridge/annotations',
  ],
  processors=[
    'com.twitter.common.scala.objectbridge.processor.ObjectBridgeProcessor'
  ]
)

java_library(name='test',
  sources=['package-info.java'],
  dependencies=[
    ':processor',
    'src/java/com/twitter/common/scala/objectbridge/annotations',
    'tests/scala/com/twitter/common/scala/objectbridge/processor:samples',
  ]
)
