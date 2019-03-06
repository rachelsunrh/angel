/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.ml.core.optimizer

import java.util.concurrent.Future

import com.tencent.angel.ml.core.conf.{MLCoreConf, SharedConf}
import com.tencent.angel.ml.core.utils.JsonUtils.{extract, fieldEqualClassName}
import com.tencent.angel.ml.core.utils.OptimizerKeys
import com.tencent.angel.ml.core.variable.{PSVariable, Variable}
import com.tencent.angel.ml.psf.optimizer.FTRLUpdateFunc
import com.tencent.angel.psagent.PSAgentContext
import org.apache.commons.logging.LogFactory
import org.json4s.JsonAST._
import org.json4s.JsonDSL._


class FTRL(override var lr: Double, val alpha: Double, val beta: Double) extends Optimizer {
  private val LOG = LogFactory.getLog(classOf[FTRL])

  override val numSlot: Int = 3

  override def update[T](variable: Variable, epoch: Int, batchSize: Int = 1): Future[T] = {
    val matrixId = variable.asInstanceOf[PSVariable].getMatrixId
    val func = new FTRLUpdateFunc(matrixId, variable.asInstanceOf[PSVariable].numFactors,
      alpha, beta, regL1Param, regL2Param, epoch, batchSize)
    PSAgentContext.get().getUserRequestAdapter.update(func).asInstanceOf[Future[T]]
  }

  override def toString: String = {
    s"FTRL alpha=$alpha beta=$beta lr=$lr regL1=$regL1Param regL2=$regL2Param"
  }

  override def toJson: JObject = {
    (OptimizerKeys.typeKey -> s"${this.getClass.getSimpleName}") ~
      (OptimizerKeys.alphaKey -> alpha) ~
      (OptimizerKeys.betaKey -> beta)
  }
}

object FTRL {
  private val conf: SharedConf = SharedConf.get()

  def fromJson(jast: JObject): FTRL = {
    assert(fieldEqualClassName[FTRL](jast, OptimizerKeys.typeKey))
    val alpha = conf.getDouble(MLCoreConf.ML_OPT_FTRL_ALPHA, MLCoreConf.DEFAULT_ML_OPT_FTRL_ALPHA)
    val beta = conf.getDouble(MLCoreConf.ML_OPT_FTRL_BETA, MLCoreConf.DEFAULT_ML_OPT_FTRL_BETA)

    new FTRL(1.0, extract[Double](jast, OptimizerKeys.betaKey, Some(alpha)).get,
      extract[Double](jast, OptimizerKeys.gammaKey, Some(beta)).get
    )
  }
}
