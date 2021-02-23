package ingest

import ingest.DataUtils.extractNano
import ingest.StreamProducer.logger
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class EventReader( kafkaProperties: Properties) {
  private val timeout: Long = 10000*1000000
  val latencies = new Array[Float](500000)

  def start = Future {
    val consumer = new KafkaConsumer[String, String](kafkaProperties, new StringDeserializer, new StringDeserializer)

    Seq(ConfigUtils.flowTopic, ConfigUtils.speedTopic).foreach { t =>
      val topicPartitions = consumer.partitionsFor(t).asScala
        .map(p => new TopicPartition(p.topic(), p.partition())).asJava
      consumer.assign(topicPartitions)
      consumer.seekToBeginning(topicPartitions)
      consumer.assignment().asScala.foreach(consumer.position)
    }


    var currentTimeNanos = System.nanoTime
    var lastConsumedTime = currentTimeNanos

    var i:Long = 0L
    var j:Int = 0

    var averageTime: Float = 0.0f

    while( currentTimeNanos - lastConsumedTime <= timeout){
      val records = if (ConfigUtils.rdma){
        consumer.RDMApoll(Duration.ofMillis(100)).asScala
      }else{
        consumer.poll(Duration.ofMillis(100)).asScala
      }

      if (records.nonEmpty) {
        currentTimeNanos = System.nanoTime()
        lastConsumedTime = currentTimeNanos
      }
      for (record <- records) {
        val elapsed = currentTimeNanos - extractNano(record.value())
        averageTime+=(elapsed/1000.0f)
        i = i + 1
        if (i % 1000 == 0) {
          println(i + "\t" + averageTime/ 1000.0f + " us")
          latencies(j) = averageTime/ 1000.0f
          averageTime = 0.0f
          j=j+1
          if (j == 500000) {
            println("Done batch ")
            latencies.foreach(x => print(x + " "))
            println("")
            j=0;
          }
        }

      }
    }
    logger.info("END OF REader")
    println("Final batch ")
    latencies.foreach(x => print(x + " "))
    println("")

  }
}
