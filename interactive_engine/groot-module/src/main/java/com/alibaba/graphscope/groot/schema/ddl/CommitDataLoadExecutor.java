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
import com.alibaba.graphscope.groot.operation.ddl.CommitDataLoadOperation;
import com.alibaba.graphscope.groot.schema.request.DdlException;
import com.alibaba.maxgraph.proto.CommitDataLoadPb;
import com.alibaba.maxgraph.proto.DataLoadTargetPb;
import com.alibaba.maxgraph.sdkcommon.common.DataLoadTarget;
import com.alibaba.maxgraph.sdkcommon.schema.EdgeKind;
import com.alibaba.maxgraph.sdkcommon.schema.GraphDef;
import com.alibaba.maxgraph.sdkcommon.schema.LabelId;
import com.alibaba.maxgraph.sdkcommon.schema.TypeDef;
import com.alibaba.maxgraph.sdkcommon.schema.TypeEnum;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

public class CommitDataLoadExecutor extends AbstractDdlExecutor {
    @Override
    public DdlResult execute(ByteString ddlBlob, GraphDef graphDef, int partitionCount)
            throws InvalidProtocolBufferException {
        CommitDataLoadPb commitDataLoadPb = CommitDataLoadPb.parseFrom(ddlBlob);
        DataLoadTargetPb dataLoadTargetPb = commitDataLoadPb.getTarget();
        String path = commitDataLoadPb.getPath();
        DataLoadTarget dataLoadTarget = DataLoadTarget.parseProto(dataLoadTargetPb);
        String label = dataLoadTarget.getLabel();
        String srcLabel = dataLoadTarget.getSrcLabel();
        String dstLabel = dataLoadTarget.getDstLabel();

        long version = graphDef.getSchemaVersion();
        if (!graphDef.hasLabel(label)) {
            throw new DdlException(
                    "label [" + label + "] not exists, schema version [" + version + "]");
        }

        GraphDef.Builder graphDefBuilder = GraphDef.newBuilder(graphDef);
        TypeDef typeDef = graphDef.getTypeDef(label);

        DataLoadTarget.Builder targetBuilder = DataLoadTarget.newBuilder(dataLoadTarget);
        if (srcLabel == null || srcLabel.isEmpty()) {
            // Vertex type
            if (typeDef.getTypeEnum() != TypeEnum.VERTEX) {
                throw new DdlException(
                        "invalid data load target [" + dataLoadTarget + "], label is not a vertex");
            }
            targetBuilder.setLabelId(typeDef.getLabelId());
        } else {
            // Edge kind
            if (typeDef.getTypeEnum() != TypeEnum.EDGE) {
                throw new DdlException(
                        "invalid data load target [" + dataLoadTarget + "], label is not an edge");
            }
            EdgeKind.Builder edgeKindBuilder = EdgeKind.newBuilder();
            LabelId edgeLabelId = graphDef.getLabelId(label);
            if (edgeLabelId == null) {
                throw new DdlException(
                        "invalid edgeLabel [" + label + "], schema version [" + version + "]");
            }
            edgeKindBuilder.setEdgeLabelId(edgeLabelId);
            targetBuilder.setLabelId(edgeLabelId.getId());
            LabelId srcVertexLabelId = graphDef.getLabelId(srcLabel);
            if (srcVertexLabelId == null) {
                throw new DdlException(
                        "invalid srcVertexLabel ["
                                + srcLabel
                                + "], schema version ["
                                + version
                                + "]");
            }
            edgeKindBuilder.setSrcVertexLabelId(srcVertexLabelId);
            targetBuilder.setSrcLabelId(srcVertexLabelId.getId());
            LabelId dstVertexLabelId = graphDef.getLabelId(dstLabel);
            if (dstVertexLabelId == null) {
                throw new DdlException(
                        "invalid dstVertexLabel ["
                                + dstLabel
                                + "], schema version ["
                                + version
                                + "]");
            }
            edgeKindBuilder.setDstVertexLabelId(dstVertexLabelId);
            targetBuilder.setDstLabelId(dstVertexLabelId.getId());
            EdgeKind edgeKind = edgeKindBuilder.build();
            if (!graphDef.hasEdgeKind(edgeKind)) {
                throw new DdlException(
                        "invalid data load target [" + dataLoadTarget + "], edgeKind not exists");
            }
        }
        version++;
        graphDefBuilder.setVersion(version);
        GraphDef newGraphDef = graphDefBuilder.build();
        List<Operation> operations = new ArrayList<>(partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            operations.add(
                    new CommitDataLoadOperation(
                            i,
                            version,
                            CommitDataLoadPb.newBuilder()
                                    .setTableIdx(commitDataLoadPb.getTableIdx())
                                    .setTarget(targetBuilder.build().toProto())
                                    .setPartitionId(i)
                                    .setPath(path)
                                    .build()));
        }
        return new DdlResult(newGraphDef, operations);
    }
}
