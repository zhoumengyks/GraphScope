/*
 * Copyright 2022 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.graphscope.graphx.store.impl

import com.alibaba.graphscope.graphx.{VertexData, VertexDataBuilder, VineyardArrayBuilder, VineyardClient}
import com.alibaba.graphscope.graphx.utils.{EIDAccessor, GrapeUtils, ScalaFFIFactory}
import org.apache.spark.internal.Logging
import org.apache.spark.util.SizeEstimator

import scala.reflect.ClassTag

class OffHeapVertexDataStore[VD: ClassTag](length : Int, localNum : Int, client : VineyardClient, val vdataBuilder : VertexDataBuilder[Long,VD]) extends AbstractVertexDataStore[VD](length, localNum, client) with Logging{
  def this(length : Int, localNum : Int, client: VineyardClient, defaultVD : VD = null){
    this(length, localNum, client,ScalaFFIFactory.newVertexDataBuilder[VD](client,length, defaultVD))
  }
  require(GrapeUtils.isPrimitive[VD])

  val arrayBuilder: VineyardArrayBuilder[VD] = vdataBuilder.getArrayBuilder
  override def get(ind: Int): VD = arrayBuilder.get(ind)

  override def set(ind: Int, vd: VD): Unit = arrayBuilder.set(ind, vd)

  override def estimatedSize: Long = {
    val res = SizeEstimator.estimate(client) + SizeEstimator.estimate(vdataBuilder) + 8
    res
  }
}

class ImmutableOffHeapVertexDataStore[VD: ClassTag](length : Int,localNum : Int, client : VineyardClient, val vertexData : VertexData[Long,VD]) extends AbstractVertexDataStore[VD](length, localNum, client) with Logging {
  require(GrapeUtils.isPrimitive[VD])

  override def get(ind: Int): VD = vertexData.getData(ind)

  override def set(ind: Int, vd: VD): Unit = {
    throw new IllegalStateException("not implemented")
  }

  override def estimatedSize: Long = {
    val res = SizeEstimator.estimate(client)  + SizeEstimator.estimate(vertexData) + 8
    res

  }
}

