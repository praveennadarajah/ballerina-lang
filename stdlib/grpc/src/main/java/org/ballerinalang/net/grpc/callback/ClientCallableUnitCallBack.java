/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.net.grpc.callback;

import org.ballerinalang.jvm.values.ErrorValue;

import java.util.concurrent.Semaphore;

/**
 * Call back class registered for client message listener service in B7a executor.
 *
 * @since 0.995.0
 */
public class ClientCallableUnitCallBack extends AbstractCallableUnitCallBack {
    private Semaphore semaphore;

    public ClientCallableUnitCallBack(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    @Override
    public void notifySuccess() {
        super.notifySuccess();
        semaphore.release();
    }
    
    @Override
    public void notifyFailure(ErrorValue error) {
        super.notifyFailure(error);
        semaphore.release();
    }
}
