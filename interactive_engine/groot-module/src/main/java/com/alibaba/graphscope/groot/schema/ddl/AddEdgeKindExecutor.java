/**
 * Copyright 2020 Alibaba Group Holding Limited.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.graphscope.groot.schema.ddl;

import com.alibaba.graphscope.groot.operation.Operation;
import com.alibaba.graphscope.groot.operation.ddl.AddEdgeKindOperation;
import com.alibaba.graphscope.groot.schema.request.DdlException;
import com.alibaba.maxgraph.proto.groot.EdgeKindPb;
import com.alibaba.maxgraph.sdkcommon.schema.EdgeKind;
import com.alibaba.maxgraph.sdkcommon.schema.GraphDef;
import com.alibaba.maxgraph.sdkcommon.schema.LabelId;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

public class AddEdgeKindExecutor extends AbstractDdlExecutor {

    @Override
    public DdlResult execute(ByteString ddlBlob, GraphDef graphDef, int partitionCount)
            throws InvalidProtocolBufferException {
        EdgeKindPb edgeKindPb = EdgeKindPb.parseFrom(ddlBlob);
        EdgeKind edgeKind = EdgeKind.parseProto(edgeKindPb);
        long version = graphDef.getSchemaVersion();

        EdgeKind.Builder edgeKindBuilder = EdgeKind.newBuilder(edgeKind);
        String edgeLabel = edgeKind.getEdgeLabel();
        LabelId edgeLabelId = graphDef.getLabelId(edgeLabel);
        if (edgeLabelId == null) {
            throw new DdlException(
                    "invalid edgeLabel [" + edgeLabel + "], schema version [" + version + "]");
        }
        edgeKindBuilder.setEdgeLabelId(edgeLabelId);

        String srcVertexLabel = edgeKind.getSrcVertexLabel();
        LabelId srcVertexLabelId = graphDef.getLabelId(srcVertexLabel);
        if (srcVertexLabelId == null) {
            throw new DdlException(
                    "invalid srcVertexLabel ["
                            + srcVertexLabel
                            + "], schema version ["
                            + version
                            + "]");
        }
        edgeKindBuilder.setSrcVertexLabelId(srcVertexLabelId);

        String dstVertexLabel = edgeKind.getDstVertexLabel();
        LabelId dstVertexLabelId = graphDef.getLabelId(dstVertexLabel);
        if (dstVertexLabelId == null) {
            throw new DdlException(
                    "invalid dstVertexLabel ["
                            + dstVertexLabel
                            + "], schema version ["
                            + version
                            + "]");
        }
        edgeKindBuilder.setDstVertexLabelId(dstVertexLabelId);

        EdgeKind newEdgeKind = edgeKindBuilder.build();
        if (graphDef.hasEdgeKind(newEdgeKind)) {
            throw new DdlException(
                    "edgeKind ["
                            + newEdgeKind
                            + "] already exists, schema version ["
                            + version
                            + "]");
        }

        GraphDef.Builder graphDefBuilder = GraphDef.newBuilder(graphDef);
        version++;
        graphDefBuilder.setVersion(version);
        graphDefBuilder.addEdgeKind(newEdgeKind);
        long tableIdx = graphDef.getTableIdx();
        tableIdx++;
        graphDefBuilder.putEdgeTableId(newEdgeKind, tableIdx);
        graphDefBuilder.setTableIdx(tableIdx);
        GraphDef newGraphDef = graphDefBuilder.build();

        List<Operation> operations = new ArrayList<>(partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            operations.add(new AddEdgeKindOperation(i, version, newEdgeKind, tableIdx));
        }
        return new DdlResult(newGraphDef, operations);
    }
}
