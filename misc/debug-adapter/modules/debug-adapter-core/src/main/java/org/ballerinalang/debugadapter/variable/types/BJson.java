/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.debugadapter.variable.types;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.Value;
import com.sun.tools.jdi.ObjectReferenceImpl;
import org.ballerinalang.debugadapter.variable.BCompoundVariable;
import org.ballerinalang.debugadapter.variable.BVariableType;
import org.ballerinalang.debugadapter.variable.JVMValueType;
import org.eclipse.lsp4j.debug.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ballerina json variable type.
 */
public class BJson extends BCompoundVariable {

    private final ObjectReferenceImpl jvmValueRef;

    public BJson(Value value, Variable dapVariable) {
        this.jvmValueRef = value instanceof ObjectReferenceImpl ? (ObjectReferenceImpl) value : null;
        dapVariable.setType(BVariableType.JSON.getString());
        dapVariable.setValue(this.getValue());
        this.setDapVariable(dapVariable);
        this.computeChildVariables();
    }

    @Override
    public String getValue() {
        return "object";
    }

    @Override
    public void computeChildVariables() {
        try {
            List<Field> fields = jvmValueRef.referenceType().allFields();
            Optional<Field> valueField = fields.stream().filter(field -> field.typeName()
                    .equals("java.util.HashMap$Node[]")).findFirst();
            if (!valueField.isPresent()) {
                return;
            }
            Value jsonValue = jvmValueRef.getValue(valueField.get());
            Map<String, Value> values = new HashMap<>();
            ((ArrayReference) jsonValue).getValues().stream().filter(Objects::nonNull).forEach(jsonMap -> {
                List<Field> jsonValueFields = ((ObjectReferenceImpl) jsonMap).referenceType().visibleFields();
                Optional<Field> jsonKeyField = jsonValueFields.stream().filter(field ->
                        field.name().equals("key")).findFirst();
                Optional<Field> jsonValueField = jsonValueFields.stream().filter(field ->
                        field.name().equals("value")).findFirst();

                if (jsonKeyField.isPresent() && jsonValueField.isPresent()) {
                    Value jsonKey = ((ObjectReferenceImpl) jsonMap).getValue(jsonKeyField.get());
                    Value jsonValue1 = ((ObjectReferenceImpl) jsonMap).getValue(jsonValueField.get());
                    values.put(getJsonKeyString(jsonKey), jsonValue1);
                }
            });
            this.setChildVariables(values);
        } catch (Exception ignored) {
            this.setChildVariables(new HashMap<>());
        }
    }

    private String getJsonKeyString(Value key) {
        ObjectReferenceImpl keyRef = key instanceof ObjectReferenceImpl ? (ObjectReferenceImpl) key : null;
        if (keyRef == null) {
            return "unknown";
        }
        if (keyRef.referenceType().name().equals(JVMValueType.BMPSTRING.getString())
                || keyRef.referenceType().name().equals(JVMValueType.NONBMPSTRING.getString())) {
            Optional<Field> valueField = keyRef.referenceType().allFields().stream()
                    .filter(field -> field.name().equals("value")).findAny();
            if (valueField.isPresent()) {
                return keyRef.getValue(valueField.get()).toString();
            }
            return "unknown";
        } else {
            return keyRef.toString();
        }
    }
}
