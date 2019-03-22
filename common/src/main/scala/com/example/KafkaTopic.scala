package com.example

import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig

import scala.util.Try

object KafkaTopic {
  val valueDataTypeField = "valueDataType"
  implicit val format = new Format[KafkaTopic] {
    def writes(o: KafkaTopic): JsValue = o match {
      case obj: KafkaTopicBytes => Json.toJson(obj)(KafkaTopicBytes.format)
      case obj: KafkaTopicString => Json.toJson(obj)(KafkaTopicString.format)
    }
    def reads(json: JsValue): JsResult[KafkaTopic] = {
      json.validate[JsObject] match {
        case JsSuccess(jsObj, _) => (jsObj \ valueDataTypeField).asOpt[String] match {
          case Some(valueDataType) => valueDataType match {
            case "bytes" => jsObj.validate[KafkaTopicBytes](KafkaTopicBytes.format)
            case "string" => jsObj.validate[KafkaTopicString](KafkaTopicString.format)
            case x => JsError(s"""$valueDataTypeField field is $x, which is not understood""")
          }
          case _ => JsError(s"$valueDataTypeField field not found or is not a string")
        }
        case JsError(err) => JsError(err)
      }
    }
  }

  def strOrNone(strRaw: String): Option[String] = {
    Option(strRaw).flatMap { str =>
      val trimmedStr = str.trim
      if (trimmedStr.isEmpty || trimmedStr.dropWhile(_ == '-').isEmpty) None else Some(trimmedStr)
    }
  }


  lazy val config: Config = ConfigFactory.load()
  lazy val defaultConfigEnvironment: Option[String] =
    Try(config.getString("environment")).toOption.flatMap(strOrNone)
  val defaultPartitions: Int = 5 // The default from Hortonworks Kafka
  val defaultReplication: Int = 3 // Probably should never be less than 3

   val defaultsOnlyJSON: JsObject =
    Json.parse(s"""{ "defaultPartitions": ${defaultPartitions}, "defaultReplication": ${defaultReplication} }""").as[JsObject]

   def readsOneTypeWithDefaults[T <: KafkaTopic](typ: String, fmt: Format[T]): JsValue => JsResult[T] = { json =>
    json.validate[JsObject].flatMap { jsObj =>
      val withDefaults = defaultsOnlyJSON ++ jsObj // jsObj fields win this merge
      (withDefaults \ valueDataTypeField).asOpt[String] match {
        case Some(typ) => withDefaults.validate[T](fmt) match {
          case validate @ JsSuccess(obj, _) =>
            if (typ == obj.valueDataType) validate else JsError(s"""$valueDataTypeField field is "${obj.valueDataType}", should be "$typ"""")
          case err: JsError => err
        }
        case _ => JsError(s"$valueDataTypeField not found or is not a string")
      }
    }
  }

   def writesWithType[T <: KafkaTopic](fmt: Format[T]): T => JsValue = { o =>
    val jsObj = Json.toJson(o)(fmt).as[JsObject]
    jsObj + (valueDataTypeField -> JsString(o.valueDataType))
  }
}
sealed abstract class KafkaTopic {
  def name: String
  def description: String
  def keyDescription: String
  def valueDescription: String
  def maxMessageSize: Int
  def global: Boolean // Global topics, e.g. "stats_file_feed_*" are not scoped to a spark job environment
  def compact: Boolean
  def retentionHours: Long
  def valueDataType: String // "bytes" or "string"
  def isNameDynamic: Boolean

  /** The number of partitions to create this topic with.  Will not change partitioning of an existing topic */
  def defaultPartitions: Int
  /** The default replication factor.  Strictly advisory */
  def defaultReplication: Int

  def fromEnvironment(environment: String): KafkaTopic
  def fromDefaultConfigEnvironment: KafkaTopic =
    fromEnvironment(KafkaTopic.defaultConfigEnvironment.getOrElse(""))

  /**
    * Consumer config for this topic that allows the maximum message size to flow through
    */
  def consumerConfig: Map[String, String] = Map[String, String](
    ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> maxMessageSize.toString
  )

  /**
    * Producer config for this topic that allows the maximum message size to flow through
    */
  def producerConfig: Map[String, String] = Map[String, String](
    ProducerConfig.MAX_REQUEST_SIZE_CONFIG -> maxMessageSize.toString
  )

  def toJson: JsObject = Json.toJson(this).as[JsObject]

  /**
    * Create a KafkaBytesProducer for this topic if able to.
    * @param brokers the Kafka brokers to produce to
    * @param partitionsByKey optional function to generate a different key for partitioning messages
    * @param kafkaConfig set various Kafka producer settings
    * @return Some(KafkaBytesProducer), or None if unable to create this type of producer.
    */
  def bytesProducer(brokers: Seq[String], partitionsByKey: Option[String => String] = None,
                    kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): Option[KafkaBytesProducer] = None
  /**
    * Create a KafkaStringProducer for this topic if able to.
    * @param brokers the Kafka brokers to produce to
    * @param partitionByKey optional function to generate a different key for partitioning messages
    * @param kafkaConfig set various Kafka producer settings
    * @return Some(KafkaStringProducer), or None if unable to create this type of producer.
    */
  def stringProducer(brokers: Seq[String], partitionByKey: Option[String => String] = None,
                     kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): Option[KafkaStringProducer] = None
}


object KafkaTopicString {
  import KafkaTopic._
  val valueDataType = "string"
  val fmt: Format[KafkaTopicString] = Json.format[KafkaTopicString]
  implicit val format = new Format[KafkaTopicString] {
    def writes(o: KafkaTopicString): JsValue = writesFunc(o)
    def reads(json: JsValue): JsResult[KafkaTopicString] = readsFunc(json)
  }
  private val readsFunc: JsValue => JsResult[KafkaTopicString] = readsOneTypeWithDefaults(valueDataType, fmt)
  private val writesFunc: KafkaTopicString => JsValue = writesWithType(fmt)
}


  case class KafkaTopicString(
           override val name: String,
           override val description: String,
           override val keyDescription: String,
           override val valueDescription: String,
           override val maxMessageSize: Int = 1000000,
           override val global: Boolean = false,
           override val compact: Boolean = false,
           override val retentionHours: Long = 168,
           override val isNameDynamic: Boolean = false,
           override val defaultPartitions: Int = KafkaTopic.defaultPartitions,
           override val defaultReplication: Int = KafkaTopic.defaultReplication
          ) extends KafkaTopic {
    override def valueDataType: String = KafkaTopicString.valueDataType
    override def fromEnvironment(environment: String): KafkaTopicString =
      if (global || environment.isEmpty) this else this.copy(global = true, name = s"$name.$environment")
    override def stringProducer(brokers: Seq[String], partitionByKey: Option[String => String] = None,
                                kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): Option[KafkaStringProducer] = {

      Some(KafkaStringProducer(brokers, this, partitionByKey, kafkaConfig))
    }
  }


object KafkaTopicBytes {
  import KafkaTopic._
  val valueDataType = "bytes"
  val fmt: Format[KafkaTopicBytes] = Json.format[KafkaTopicBytes]
  implicit val format = new Format[KafkaTopicBytes] {
    def writes(o: KafkaTopicBytes): JsValue = writesFunc(o)
    def reads(json: JsValue): JsResult[KafkaTopicBytes] = readsFunc(json)
  }
  private val readsFunc: JsValue => JsResult[KafkaTopicBytes] = readsOneTypeWithDefaults(valueDataType, fmt)
  private val writesFunc: KafkaTopicBytes => JsValue = writesWithType(fmt)
}


case class KafkaTopicBytes(
        override val name: String,
        override val description: String,
        override val keyDescription: String,
        override val valueDescription: String,
        override val maxMessageSize: Int = 1000000,
        override val global: Boolean = false,
        override val compact: Boolean = false,
        override val retentionHours: Long = 168,
        override val isNameDynamic: Boolean = false,
        override val defaultPartitions: Int = KafkaTopic.defaultPartitions,
        override val defaultReplication: Int = KafkaTopic.defaultReplication
       ) extends KafkaTopic {
  override def valueDataType: String = KafkaTopicBytes.valueDataType
  override def fromEnvironment(environment: String): KafkaTopicBytes =
    if (!global && environment.isEmpty) {
      throw new IllegalArgumentException("environment was not specified")
    } else if (global || environment.isEmpty) this else this.copy(global = true, name = s"$name.$environment")
  override def bytesProducer(brokers: Seq[String], partitionByKey: Option[String => String] = None,
                             kafkaConfig: Map[String, String] = KafkaProducerTrait.defaultConfig): Option[KafkaBytesProducer] = {

    Some(KafkaBytesProducer(brokers, this, partitionByKey, kafkaConfig))
  }
}

