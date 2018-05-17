package kr.ac.yonsei.delab.addb_srconnector.rdd

import org.apache.spark.rdd.RDD
import org.apache.spark.TaskContext
import org.apache.spark.SparkContext
import org.apache.spark.Partition
import kr.ac.yonsei.delab.addb_srconnector._
import kr.ac.yonsei.delab.addb_srconnector.partition._
import org.apache.spark.sql.{DataFrame, SQLContext, Row}
import org.apache.spark.sql.sources._
import kr.ac.yonsei.delab.addb_srconnector.util
import org.apache.spark.sql.types._
import scala.reflect.ClassTag
import java.text.NumberFormat
import scala.collection.JavaConversions._

class ADDBRDD (
    @transient val sc: SparkContext,
    val redisConfig: RedisConfig,
    val redisTable: RedisTable,
    val requiredColumns: Array[String],
    val filter: Array[Filter]
    ) extends RDD[RedisRow] (sc, Seq.empty)
   // )  extends RDD[RedisRow] (sc, Seq.empty)
  {
  
  override protected def getPreferredLocations(split: Partition): Seq[String] = {
    logInfo( s"[WONKI] : getPreferredLocations called")
    Seq(split.asInstanceOf[RedisPartition].location)
  }
  
  override protected def getPartitions: Array[Partition] = {
    logInfo( s"[WONKI] : getPartitions called")
    val redisStore = redisConfig.getRedisStore()
    val sourceinfos = redisStore.getTablePartitions(redisTable)
    var i = 0
    sourceinfos.map { mem =>
      val loc = mem._1
      logInfo( s"[WONKI] : getPartitions mem 1 : $mem._1")
      val sources : Array[String] = mem._2
      logInfo( s"[WONKI] : getPartitions mem 2 : $mem._2")
      val partition = new RedisPartition(i, redisConfig, loc, sources);
      i += 1
      partition
    }.toArray
    
  }
  
  override def compute(split: Partition, context: TaskContext) : Iterator[RedisRow] = {
    //Iterator[Row] = {
    logInfo( s"[WONKI] : compute called")
    val partition = split.asInstanceOf[RedisPartition]
    val redisStore = redisConfig.getRedisStore()
    redisStore.scan(redisTable, partition.location, partition.partition, requiredColumns)
  }  
}

class RedisRDDAdaptor(
  val prev: RDD[RedisRow],
  val requiredColumns: Array[StructField],
  val filters: Array[Filter],
  val schema: org.apache.spark.sql.types.StructType
) extends RDD[Row]( prev ) {
  //with Logging {
  //  val metricLogger = LoggerFactory.getLogger( "org.skt.spark.r2.metric.row.fetch" )

  def castToTarget(value: String, dataType: DataType) = {
    dataType match {
      case IntegerType => value.toInt
      case DoubleType => value.toDouble
      case StringType => value.toString
      case _ => value.toString
    }
  }
  
  override def getPartitions: Array[Partition] = prev.partitions

  override def compute(split: Partition, context: TaskContext): Iterator[Row] = {
    prev.compute(split, context).map {
      redisRow =>
        logInfo(s"[WONKI] hochul")
        val columns: Array[Any] = requiredColumns.map { column =>
          redisRow.columns.foreach{x => logInfo(s"[wonki] : column1 : ${x._1.getClass} , ${x._2.getClass}")
              logInfo(s"[wonki] : column2 : " + redisRow.columns.get(x._1).get )
          }
          
          val value = redisRow.columns.getOrElse(column.name, null)
//          val value = redisRow.columns.get(column.name).get
          logInfo(s"[WONKI] : compute : $value  : $column.name  $column.dataType")
          castToTarget(value, column.dataType)
        }
        val row = Row.fromSeq(columns.toSeq)
        row
    }
    }
}
