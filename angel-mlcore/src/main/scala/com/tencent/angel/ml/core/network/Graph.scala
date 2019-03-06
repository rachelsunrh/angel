package com.tencent.angel.ml.core.network

import com.tencent.angel.ml.core.PredictResult
import com.tencent.angel.ml.core.conf.SharedConf
import com.tencent.angel.ml.core.network.layers.{Trainable, _}
import com.tencent.angel.ml.core.optimizer.loss.LossFunc
import com.tencent.angel.ml.core.utils.JsonUtils.{J2Pretty, layer2Json}
import com.tencent.angel.ml.core.utils.{DataCache, TimeStats}
import com.tencent.angel.ml.core.variable.{VariableManager, VariableProvider}
import com.tencent.angel.ml.math2.matrix.Matrix
import com.tencent.angel.ml.math2.utils.LabeledData
import com.tencent.angel.ml.math2.vector.Vector
import org.apache.commons.logging.{Log, LogFactory}
import org.json4s.JsonAST.{JField, JObject}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


trait EvnContext


class Graph(val provider: VariableProvider, val placeHolder: PlaceHolder, val conf: SharedConf, val taskNum: Int) {
  private val LOG: Log = LogFactory.getLog(classOf[Graph])

  protected val inputLayers = new ListBuffer[InputLayer]()
  protected var lossLayer: LossLayer = _
  protected val trainableLayer = new ListBuffer[Trainable]()
  private val dataCache = new DataCache()

  val timeStats = new TimeStats()

  def normalFactor: Double = 1.0 / (placeHolder.getBatchSize * taskNum)

  protected var lr: Double = SharedConf.learningRate

  def addInputLayer(layer: InputLayer): Unit = {
    inputLayers.append(layer)
  }

  def getALLInputLayers: List[InputLayer] = inputLayers.toList

  def getInputLayer(name: String): InputLayer = {
    val layerOption = inputLayers.collectFirst {
      case layer: InputLayer if layer.name == name => layer
    }

    layerOption.getOrElse(null.asInstanceOf[InputLayer])
  }

  def setLossLayer(layer: LossLayer): Unit = {
    lossLayer = layer
  }

  def getLossLayer: LossLayer = lossLayer

  def getLossFunc: LossFunc = lossLayer.lossFunc

  def addTrainableLayer(layer: Trainable): Unit = {
    trainableLayer.append(layer)
  }

  def getALLTrainableLayers: List[Trainable] = {
    trainableLayer.toList
  }

  def getTrainableLayer(name: String): Trainable = {
    val trainableOption = trainableLayer.collectFirst {
      case layer: Layer if layer.name == name => layer.asInstanceOf[Trainable]
    }

    trainableOption.getOrElse(null.asInstanceOf[Trainable])
  }

  protected def deepFirstDown(layer: Layer)(predicate: Layer => Boolean, action: Layer => Unit): Unit = {
    if (predicate(layer)) {
      action(layer)
      layer match {
        case l: JoinLayer =>
          l.inputLayers.foreach { lowerLayer =>
            deepFirstDown(lowerLayer)(predicate, action)
          }
        case l: LinearLayer => deepFirstDown(l.inputLayer)(predicate, action)
        case _: InputLayer =>
      }
    }
  }


  /** **********************************************************************************
    * training
    */

  def setLR(lr: Double): Unit = {
    this.lr = lr
    trainableLayer.foreach { trainable =>
      trainable.optimizer.setLR(lr)
    }
  }

  def getLR: Double = this.lr

  def feedData(data: Array[LabeledData]): Unit = {
    placeHolder.feedData(data)
  }

  // forward
  def calForward(): Double = {
    val start = System.currentTimeMillis()
    clearCache()
    val loss = lossLayer.calLoss()
    val end = System.currentTimeMillis()

    timeStats.forwardTime += end - start

    loss
  }

  // backward
  def calBackward(): Unit = {
    val start = System.currentTimeMillis()
    inputLayers.foreach { layer => layer.backward(layer) }
    val end = System.currentTimeMillis()

    timeStats.backwardTime += end - start
  }


  /** **********************************************************************************
    * predict
    */

  def predict(): List[PredictResult] = {
    val start = System.currentTimeMillis()
    clearCache()
    val res = lossLayer.predict()
    val end = System.currentTimeMillis()
    timeStats.predictTime += end - start

    res
  }

  /** **********************************************************************************
    * Matrix Cache
    */

  def put2Cache(name: String, mat: Matrix): Unit = {
    dataCache.addMatrix(name, mat)
  }

  def put2Cache(name: String, vec: Vector): Unit = {
    dataCache.addVector(name, vec)
  }

  def isMatrixInCache(name: String): Boolean = {
    dataCache.hasMatrix(name)
  }

  def isVectorInCache(name: String): Boolean = {
    dataCache.hasVector(name)
  }

  def getMatrixFromCache(name: String): Matrix = {
    dataCache.getMatrix(name)
  }

  def getVectorFromCache(name: String): Vector = {
    dataCache.getVector(name)
  }

  def clearCache(): Unit = {
    dataCache.clearAll()
  }

  /** **********************************************************************************
    * toString/toJson
    */

  override def toString: String = {
    val str = new StringBuilder
    deepFirstDown(lossLayer.asInstanceOf[Layer])(_ => true, layer => str.append(layer.toString + "\n"))
    str.toString()
  }

  def toJson: String = {
    implicit val jsonMap: mutable.HashMap[String, JField] = new mutable.HashMap[String, JField]()
    layer2Json(lossLayer.asInstanceOf[Layer])
    J2Pretty(JObject(jsonMap.values.toList))
  }
}
