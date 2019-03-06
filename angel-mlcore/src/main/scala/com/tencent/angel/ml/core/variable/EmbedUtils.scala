package com.tencent.angel.ml.core.variable

import com.tencent.angel.ml.core.network.Graph
import com.tencent.angel.ml.math2.{MFactory, VFactory}
import com.tencent.angel.ml.math2.matrix.{MapMatrix, Matrix}
import com.tencent.angel.ml.math2.storage._
import com.tencent.angel.ml.math2.vector._
import java.lang.{Long => JLong}
import java.util.{HashMap => JHashMap, Map => JMap}

import com.tencent.angel.ml.core.utils.MLException

object EmbedUtils {
  def geneMatrix(features: Matrix, embeddings: JMap[JLong, Vector]): Matrix = {
    val rows = (0 until features.getNumRows).toArray.map { rId =>
      val row = features.getRow(rId)
      val partitions = row.getStorage match {
        case s: IntDoubleDenseVectorStorage =>
          s.getValues.zipWithIndex.map { case (value, idx) =>
            val vec = embeddings.get(idx.toLong)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: IntDoubleSparseVectorStorage =>
          s.getIndices.sorted.map { idx =>
            val vec = embeddings.get(idx.toLong)
            val value = s.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: IntDoubleSortedVectorStorage =>
          s.getValues.zip(s.getIndices).map { case (value, idx) =>
            val vec = embeddings.get(idx.toLong)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: LongDoubleSparseVectorStorage =>
          s.getIndices.sorted.map { idx =>
            val vec = embeddings.get(idx)
            val value = s.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: LongDoubleSortedVectorStorage =>
          s.getValues.zip(s.getIndices).map { case (value, idx) =>
            val vec = embeddings.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: IntFloatDenseVectorStorage =>
          s.getValues.zipWithIndex.map { case (value, idx) =>
            val vec = embeddings.get(idx.toLong)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: IntFloatSparseVectorStorage =>
          s.getIndices.sorted.map { idx =>
            val vec = embeddings.get(idx.toLong)
            val value = s.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: IntFloatSortedVectorStorage =>
          s.getValues.zip(s.getIndices).map { case (value, idx) =>
            val vec = embeddings.get(idx.toLong)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: LongFloatSparseVectorStorage =>
          s.getIndices.sorted.map { idx =>
            val vec = embeddings.get(idx)
            val value = s.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
        case s: LongFloatSortedVectorStorage =>
          s.getValues.zip(s.getIndices).map { case (value, idx) =>
            val vec = embeddings.get(idx)
            if (value == 1) {
              vec
            } else {
              vec.mul(value)
            }
          }
      }

      partitions.head match {
        case v: IntDoubleVector =>
          VFactory.compIntDoubleVector(
            v.dim().toInt * partitions.length, partitions.map(_.asInstanceOf[IntDoubleVector]), v.dim().toInt
          )
        case v: IntFloatVector =>
          VFactory.compIntFloatVector(
            v.dim().toInt * partitions.length, partitions.map(_.asInstanceOf[IntFloatVector]), v.dim().toInt
          )
        case _ => throw MLException("Vector type is not supported!")
      }
    }

    if (rows.head.getType.isDouble) {
      MFactory.rbCompIntDoubleMatrix(rows.map(_.asInstanceOf[CompIntDoubleVector]))
    } else if (rows.head.getType.isFloat) {
      MFactory.rbCompIntFloatMatrix(rows.map(_.asInstanceOf[CompIntFloatVector]))
    } else {
      throw MLException("Vector type is not supported!")
    }
  }

  private def mergeUpdate(map: JMap[JLong, Vector], key: Long, update: Vector, value: Double): Unit = {
    if (!map.containsKey(key)) {
      if (value == 1) map.put(key, update)
      else map.put(key, update.imul(value))
    } else {
      if (value == 1) map.get(key).iadd(update)
      else map.get(key).iadd(update.imul(value))
    }
  }

  private def getPartitions(backward: Matrix, rId: Int): Array[Vector] = {
    val vec = backward.getRow(rId)
    val method = vec.getClass.getDeclaredMethod("getPartitions")
    method.invoke(vec).asInstanceOf[Array[Vector]]
  }

  def calGradient(features: Matrix, backward: Matrix)(implicit graph: Graph): Matrix = {
    val gradMap: JHashMap[JLong, Vector] = new JHashMap[JLong, Vector]()

    (0 until features.getNumRows).foreach { rId =>
      val row = features.getRow(rId)
      val partitions = getPartitions(backward, rId)

      row.getStorage match {
        case s: IntDoubleSparseVectorStorage =>
          s.getIndices.sorted.zipWithIndex.foreach { case (key, idx) =>
            val value = s.get(key)
            val update = partitions(idx)
            mergeUpdate(gradMap, key.toLong, update, value)
          }
        case s: IntFloatSparseVectorStorage =>
          s.getIndices.sorted.zipWithIndex.foreach { case (key, idx) =>
            val value = s.get(key)
            val update = partitions(idx)
            mergeUpdate(gradMap, key.toLong, update, value)
          }
        case s: LongDoubleSparseVectorStorage =>
          s.getIndices.sorted.zipWithIndex.foreach { case (key, idx) =>
            val value = s.get(key)
            val update = partitions(idx)
            mergeUpdate(gradMap, key, update, value)
          }
        case s: LongFloatSparseVectorStorage =>
          s.getIndices.sorted.zipWithIndex.foreach { case (key, idx) =>
            val value = s.get(key)
            val update = partitions(idx)
            mergeUpdate(gradMap, key, update, value)
          }
        case _ => throw MLException("Data type is not support!")
      }
    }

    val iter = gradMap.keySet().iterator()
    val normalFactor = graph.normalFactor
    while (iter.hasNext) {
      val key = iter.next()
      gradMap.get(key).imul(normalFactor)
    }

    new MapMatrix(0, 0, gradMap)
  }
}
