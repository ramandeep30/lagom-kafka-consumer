package com.example

import java.util.Properties
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ ByteArraySerializer, StringSerializer }
import org.apache.kafka.common.utils.Utils
import org.slf4j.LoggerFactory

/**
  * Common KafkaProducerTrait mixin
  * @see
  * Blog on writing to Kafka - [[http://allegro.tech/2015/08/spark-kafka-integration.html]]
  * See also this blog - [[http://www.michael-noll.com/blog/2014/10/01/kafka-spark-streaming-integration-example-tutorial/]]
  */
trait KafkaProducerTrait[K, V] {
  def brokers: Seq[String]

  def topic: String

  def props: Properties

  def partitionByKey: Option[K => String]

  def producer: org.apache.kafka.clients.producer.KafkaProducer[K, V]

  def getNumPartitions(): Int = producer.partitionsFor(topic).size

  /**
    * Close the Kafka producer and clean up
    */
  def close(): Unit = producer.close()

  private def recordWithPartition(record: ProducerRecord[K, V], numPartitions: Int, keyToPartitionString: K => String) = {
    val partitionByString: String = keyToPartitionString(record.key)
    if (partitionByString.isEmpty) {
      // If partitionByString returns empty string, then do not partition based on empty string,
      // otherwise messages will all go to the same topic based on empty string hash code.
      record
    } else {
      // This algorithm attempts to match the default Kafka partition calculation from a key.
      val partitionNum: Int = Utils.abs(Utils.murmur2(Utils.utf8(partitionByString))) % numPartitions
      new ProducerRecord[K, V](topic, partitionNum, record.key, record.value)
    }
  }

  /**
    * Put each key/message string pair, where the key is used for partitioning,
    * calculated optionally from the partitionByKey function, if defined.
    */
  def putRecords(records: Seq[ProducerRecord[K, V]]): Seq[java.util.concurrent.Future[RecordMetadata]] = {
    val recordsWithPartition = partitionByKey match {
      case None => records
      case Some(keyToPartitionString) =>
        val numPartitions = getNumPartitions() // Only fetch the partition count if needed
        records.map { record => recordWithPartition(record, numPartitions, keyToPartitionString) }
    }
    recordsWithPartition.map(putRecordRaw)
  }

  /**
    * Put key/message string pair, where the key is used for partitioning.
    */
  def putRecord(record: ProducerRecord[K, V]): java.util.concurrent.Future[RecordMetadata] = {
    val recordWithPartitionMaybe = partitionByKey match {
      case None => record
      case Some(keyToPartitionString) =>
        val numPartitions = getNumPartitions() // Only fetch the partition count if needed
        recordWithPartition(record, numPartitions, keyToPartitionString)
    }
    putRecordRaw(recordWithPartitionMaybe)
  }

  /**
    * Put key/message string pair, where the key is used for partitioning.
    */
  private def putRecordRaw(record: ProducerRecord[K, V]): java.util.concurrent.Future[RecordMetadata] =
    producer.send(record, Kafka.LogErrorCallback)

  /**
    * Put a string message. Note the messages will be partitioned randomly since no partition key is provided.
    */
  def putValue(value: V) {
    putRecord(new ProducerRecord[K, V](topic, value))
  }

  /**
    * Put a sequence of string messages. Note the messages will be partitioned randomly since no partition key is provided.
    */
  def putValues(values: Seq[V]) {
    putRecords(values.map(v => new ProducerRecord[K, V](topic, v)))
  }

  /**
    * Put a key/message string pair, where the key is used for partitioning.
    */
  def putKeyValue(kv: (K, V)) {
    putRecord(new ProducerRecord[K, V](topic, kv._1, kv._2))
  }

  /**
    * Put a sequence of key/message string pairs, where the key is used for partitioning.
    */
  def putKeyValues(keyValues: Seq[(K, V)]) {
    putRecords(keyValues.map(kv => new ProducerRecord[K, V](topic, kv._1, kv._2)))
  }
}

object KafkaProducerTrait {
  /**
    * Default Kafka producer config
    */
  val defaultConfig: Map[String, String] = Map.empty[String, String]
}

/**
  * Create a new Kafka String producer from which to put messages
  *
  * @param brokers is a list of host:port of one or more Kafka brokers (don't need to list all, just one or two)
  * @param topic that data will be posted to
  */
case class KafkaStringProducer(
                                override val brokers: Seq[String],
                                override val topic: String,
                                kafkaConfig: Map[String, String],
                                override val partitionByKey: Option[String => String]
                              ) extends KafkaProducerTrait[String, String] {
  val props = new Properties()
  for ((prop, value) <- kafkaConfig) props.put(prop, value)
  props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip")
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers.mkString(","))
  props.put(ProducerConfig.ACKS_CONFIG, "all")
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

  override val producer = new org.apache.kafka.clients.producer.KafkaProducer[String, String](props)

  def deleteRecord(key: String) {
    putRecord(new ProducerRecord[String, String](topic, key, None.orNull))
  }
}
object KafkaStringProducer {
  def apply(brokers: Seq[String], topic: KafkaTopicString, partitionByKey: Option[String => String] = None,
            kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): KafkaStringProducer = {

    require(topic.global, s"KafkaStringProducer called on a template topic ${topic.name} - " +
      s"you should only create a producer for a global topic or one templated from an environment - call topic.fromEnvironment(environment)")

    apply(brokers = brokers, topic = topic.name, kafkaConfig = topic.producerConfig ++ kafkaConfig, partitionByKey = partitionByKey)
  }
}

/**
  * Create a new Kafka Bytes producer from which to put messages
  *
  * @param brokers is a list of host:port of one or more Kafka brokers (don't need to list all, just one or two)
  * @param topic that data will be posted to
  */
case class KafkaBytesProducer(
                               override val brokers: Seq[String],
                               override val topic: String,
                               val kafkaConfig: Map[String, String],
                               override val partitionByKey: Option[String => String]
                             ) extends KafkaProducerTrait[String, Array[Byte]] {
  val props = new Properties()
  for ((prop, value) <- kafkaConfig) props.put(prop, value)
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers.mkString(","))
  props.put(ProducerConfig.ACKS_CONFIG, "all")
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)

  override val producer = new org.apache.kafka.clients.producer.KafkaProducer[String, Array[Byte]](props)

  def deleteRecord(key: String) {
    putRecord(new ProducerRecord[String, Array[Byte]](topic, key, None.orNull))
  }
}
object KafkaBytesProducer {
  def apply(brokers: Seq[String], topic: KafkaTopicBytes, partitionByKey: Option[String => String] = None,
            kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): KafkaBytesProducer = {

    require(topic.global, s"KafkaBytesProducer called on a template topic ${topic.name} - " +
      s"you should only create a producer for a global topic or one templated from an environment - call topic.fromEnvironment(environment)")

    apply(brokers = brokers, topic = topic.name, kafkaConfig = topic.producerConfig ++ kafkaConfig, partitionByKey = partitionByKey)
  }
}

object Kafka {
  val defaultZKEndpoints: Seq[String] = List("127.0.0.1:2181")
  val defaultBrokers: Seq[String] = List("127.0.0.1:9092")

  def pretty(meta: RecordMetadata): String = s"${meta.topic}:part-${meta.partition}:offset-${meta.offset}"

  def putKeyValues(brokers: Seq[String], topic: KafkaTopicString, keyValues: Seq[(String, String)],
                   partitionByKey: Option[String => String] = None, kafkaConfig: Map[String, String] = Map[String, String]()) {
    val kafkaProducer = KafkaStringProducer(brokers, topic, partitionByKey = partitionByKey, kafkaConfig = kafkaConfig)
    kafkaProducer.putKeyValues(keyValues)
    kafkaProducer.close()
  }

  def putKeyByteValues(brokers: Seq[String], topic: KafkaTopicBytes, keyValues: Seq[(String, Array[Byte])],
                       partitionByKey: Option[String => String] = None, kafkaConfig: Map[String, String] = Map[String, String]()) {
    val kafkaProducer = KafkaBytesProducer(brokers, topic, partitionByKey = partitionByKey, kafkaConfig = kafkaConfig)
    kafkaProducer.putKeyValues(keyValues)
    kafkaProducer.close()
  }

  /**
    * This is a common partitionByKey strategy for use, where a multi-valued key string is joined by a ':' colon character.
    * By common convention the first value of the key is the system, and this function extracts that first string before the first ':'.
    */
  val PartitionByKeyUpToFirstColon: Option[String => String] = Some({ key =>
    val parts = key.split(':')
    // There will always be a non-empty array unless ":" is the key (very odd)
    // So this may seem paranoid, but we NEVER want exceptions in Scala code
    if (parts.length > 0) parts(0) else ""
  })

  object LogErrorCallback extends Callback {
    private val log = LoggerFactory.getLogger(LogErrorCallback.getClass)

    def onCompletion(metadata: RecordMetadata, exception: Exception) {
      Option(exception) match {
        case Some(err) => log.error("Send callback returns the following exception", err)
        case None => log.debug(s"Kafka write completion - ${pretty(metadata)}")
      }
    }
  }
}
