package com.tencent.angel.ml.core

import com.tencent.angel.ml.core.conf.SharedConf
import com.tencent.angel.ml.core.data.DataBlock
import com.tencent.angel.ml.core.network.EvnContext
import com.tencent.angel.ml.core.network.layers.PlaceHolder
import com.tencent.angel.ml.core.utils.RowTypeUtils
import com.tencent.angel.ml.core.variable.VarState.VarState
import com.tencent.angel.ml.core.variable.{Variable, VariableManager, VariableProvider}
import com.tencent.angel.ml.math2.matrix.Matrix
import com.tencent.angel.ml.math2.utils.{LabeledData, RowType}
import com.tencent.angel.ml.math2.vector.Vector


abstract class MLModel {
  val dataFormat: String = SharedConf.inputDataFormat
  val indexRange: Long = SharedConf.indexRange
  val validIndexNum: Long = SharedConf.modelSize
  val modelType: RowType = SharedConf.modelType
  val isSparseFormat: Boolean = dataFormat == "libsvm" || dataFormat == "dummy"

  protected val placeHolder: PlaceHolder
  protected val variableManager: VariableManager
  protected val variableProvider: VariableProvider

  def keyType: String = RowTypeUtils.keyType(modelType)

  def valueType: String = RowTypeUtils.valueType(modelType)

  def storageType: String = RowTypeUtils.storageType(modelType)

  def addVariable(variable: Variable): Unit = {
    variableManager.addVariable(variable)
  }

  def getVariable(name: String): Variable = {
    variableManager.getVariable(name)
  }

  def getAllVariables: List[Variable] = {
    variableManager.getALLVariables
  }

  def hasVariable(v: Variable): Boolean = variableManager.hasVariable(v)

  def hasVariable(name: String): Boolean = variableManager.hasVariable(name)

  def putSlot(v: Variable, g: Matrix): Unit = {
    if (variableManager.hasSlot(v.name)) {
      variableManager.getSlot(v.name).iadd(g)
    } else {
      variableManager.putSlot(v, g)
    }
  }

  def getSlot(name: String): Matrix = {
    variableManager.getSlot(name)
  }

  def getAllSlots: Map[String, Matrix] = {
    variableManager.getAllSlots
  }

  def hasSlot(name: String): Boolean = variableManager.hasSlot(name)

  def putGradient(v: Variable, g: Matrix): Unit = putSlot(v, g)

  def getAllGradients: Map[String, Matrix] = getAllSlots

  def getGradient(name: String): Matrix = getSlot(name)

  def hasGradient(name: String): Boolean = hasSlot(name)

  def feedData(data: Array[LabeledData]): Unit = {
    placeHolder.feedData(data)
  }

  //---------------------Training Cycle
  def createMatrices(envCtx: EvnContext): Unit = {
    variableManager.createALL(envCtx)
  }

  def init(taskId: Int = 0): Unit = {
    variableManager.initALL(taskId)
  }

  def pullParams(epoch: Int, indices: Vector = null): Unit = {
    variableManager.pullALL(epoch, indices)
  }

  def pushSlot(lr: Double): Unit = {
    variableManager.pushALL(lr)
  }

  def update[T](epoch: Int, batchSize: Int): Unit = {
    variableManager.updateALL[T](epoch, batchSize)
  }

  def loadModel(envCtx: EvnContext, path: String): Unit = {
    variableManager.loadALL(envCtx, path)
  }

  def setState(state: VarState): Unit = {
    variableManager.setAllState(state)
  }

  def saveModel(envCtx: EvnContext, path: String): Unit = {
    variableManager.saveALL(envCtx, path)
  }

  //---------------------Predict
  def predict(storage: DataBlock[LabeledData]): List[PredictResult]

  def predict(storage: LabeledData): PredictResult
}
