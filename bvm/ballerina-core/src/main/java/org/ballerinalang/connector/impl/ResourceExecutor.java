/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.connector.impl;

import org.ballerinalang.bre.bvm.CallableUnitCallback;
import org.ballerinalang.bre.bvm.WorkerExecutionContext;
import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.connector.api.Resource;
import org.ballerinalang.model.values.BFunctionPointer;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.persistence.states.RuntimeStates;
import org.ballerinalang.persistence.states.State;
import org.ballerinalang.runtime.Constants;
import org.ballerinalang.util.codegen.FunctionInfo;
import org.ballerinalang.util.observability.ObserverContext;
import org.ballerinalang.util.program.BLangFunctions;
import org.ballerinalang.util.program.BLangVMUtils;
import org.ballerinalang.util.program.CompensationTable;
import org.ballerinalang.util.transactions.LocalTransactionInfo;
import org.ballerinalang.util.transactions.TransactableCallableUnitCallback;
import org.ballerinalang.util.transactions.TransactionResourceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code ResourceExecutor} This provides the implementation to execute resources within Ballerina.
 *
 * @since 0.94
 */
public class ResourceExecutor {

    /**
     * This method will execute the resource, given required details.
     * And it will use the callback to notify interested parties about the
     * outcome of the execution.
     *
     * @param resource         to be executed.
     * @param responseCallback to notify.
     * @param properties       to be passed to context.
     * @param observerContext  observer context.
     * @param context          worker execution context.
     * @param bValues          for parameters.
     */
    public static void execute(Resource resource, CallableUnitCallback responseCallback,
                               Map<String, Object> properties, ObserverContext observerContext,
                               WorkerExecutionContext context, BValue... bValues) throws BallerinaConnectorException {
        if (resource == null || responseCallback == null) {
            throw new BallerinaConnectorException("invalid arguments provided");
        }
        List<BValue> args = new ArrayList<>();
        args.add(resource.getService().getBValue());
        args.addAll(Arrays.asList(bValues));
        FunctionInfo resourceInfo = resource.getResourceInfo();
        if (properties != null) {
            Object interruptible = properties.get(Constants.IS_INTERRUPTIBLE);
            if (interruptible != null && (boolean) interruptible) {
                String stateId = UUID.randomUUID().toString();
                properties.put(Constants.STATE_ID, stateId);
                RuntimeStates.add(new State(context, stateId));
                context.interruptible = true;
            }
            context.globalProps.putAll(properties);
            if (properties.get(Constants.GLOBAL_TRANSACTION_ID) != null) {
                String globalTransactionId = properties.get(Constants.GLOBAL_TRANSACTION_ID).toString();
                LocalTransactionInfo localTransactionInfo = new LocalTransactionInfo(
                        globalTransactionId,
                        properties.get(Constants.TRANSACTION_URL).toString(), "2pc");
                context.setLocalTransactionInfo(localTransactionInfo);
                registerTransactionInfection(responseCallback, globalTransactionId, context);
            }
        }
        //required for tracking compensations
        context.globalProps.put(Constants.COMPENSATION_TABLE, CompensationTable.getInstance());
        BLangVMUtils.setServiceInfo(context, resource.getService().getServiceInfo());
        BLangFunctions.invokeServiceCallable(resourceInfo, context, observerContext, args.toArray(new BValue[0]),
                responseCallback);
    }

    private static void registerTransactionInfection(CallableUnitCallback responseCallBack, String globalTransactionId,
                                                     WorkerExecutionContext workerExecutionContext) {
        if (globalTransactionId != null && responseCallBack instanceof TransactableCallableUnitCallback) {
            TransactableCallableUnitCallback trxCallBack = (TransactableCallableUnitCallback) responseCallBack;
            TransactionResourceManager manager = TransactionResourceManager.getInstance();
            BFunctionPointer onAbort = trxCallBack.getTransactionOnAbort();
            BFunctionPointer onCommit = trxCallBack.getTransactionOnCommit();
            manager.registerParticipation(globalTransactionId, onCommit, onAbort, workerExecutionContext);
        }
    }
}
