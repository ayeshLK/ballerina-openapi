// Copyright (c) 2023 WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package io.ballerina.openapi.service.mapper.type;

import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.RecordFieldWithDefaultValueNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.openapi.service.diagnostic.DiagnosticMessages;
import io.ballerina.openapi.service.diagnostic.ExceptionDiagnostic;
import io.ballerina.openapi.service.mapper.CommonData;
import io.ballerina.openapi.service.model.ModuleMemberVisitor;
import io.ballerina.openapi.service.utils.MapperCommonUtils;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.ballerina.openapi.service.utils.MapperCommonUtils.getRecordFieldTypeDescription;
import static io.ballerina.openapi.service.utils.MapperCommonUtils.getTypeName;

public class RecordTypeMapper extends TypeMapper {

    public RecordTypeMapper(TypeReferenceTypeSymbol typeSymbol, CommonData commonData) {
        super(typeSymbol, commonData);
    }

    @Override
    public Schema getReferenceTypeSchema(Map<String, Schema> components) {
        RecordTypeSymbol recordTypeSymbol = (RecordTypeSymbol) typeSymbol.typeDescriptor();
        return getSchema(recordTypeSymbol, components, name, commonData).description(description);
    }

    public static Schema getSchema(RecordTypeSymbol typeSymbol, Map<String, Schema> components,
                                   String recordName, CommonData commonData) {
        ObjectSchema schema = new ObjectSchema();
        Set<String> requiredFields = new HashSet<>();

        Map<String, RecordFieldSymbol> recordFieldMap = new LinkedHashMap<>(typeSymbol.fieldDescriptors());
        List<Schema> allOfSchemaList = mapIncludedRecords(typeSymbol, components, recordFieldMap, commonData);

        Map<String, Schema> properties = getRecordFieldsMapping(recordFieldMap, components, requiredFields,
                recordName, commonData);

        Optional<TypeSymbol> restFieldType = typeSymbol.restTypeDescriptor();
        if (restFieldType.isPresent()) {
            if (!restFieldType.get().typeKind().equals(TypeDescKind.ANYDATA)) {
                Schema restFieldSchema = ComponentMapper.getTypeSchema(restFieldType.get(), components, commonData);
                schema.additionalProperties(restFieldSchema);
            }
        } else {
            schema.additionalProperties(false);
        }

        schema.setProperties(properties);
        schema.setRequired(requiredFields.stream().toList());
        if (!allOfSchemaList.isEmpty()) {
            ObjectSchema schemaWithAllOf = new ObjectSchema();
            allOfSchemaList.add(schema);
            schemaWithAllOf.setAllOf(allOfSchemaList);
            return schemaWithAllOf;
        }
        return schema;
    }

    static List<Schema> mapIncludedRecords(RecordTypeSymbol typeSymbol, Map<String, Schema> components,
                                           Map<String, RecordFieldSymbol> recordFieldMap, CommonData commonData) {
        List<Schema> allOfSchemaList = new ArrayList<>();
        List<TypeSymbol> typeInclusions = typeSymbol.typeInclusions();
        for (TypeSymbol typeInclusion : typeInclusions) {
            // Type inclusion in a record is a TypeReferenceType and the referred type is a RecordType
            if (typeInclusion.typeKind() == TypeDescKind.TYPE_REFERENCE &&
                    ((TypeReferenceTypeSymbol) typeInclusion).typeDescriptor().typeKind() == TypeDescKind.RECORD) {
                Schema includedRecordSchema = new Schema();
                includedRecordSchema.set$ref(getTypeName(typeInclusion));
                allOfSchemaList.add(includedRecordSchema);
                ComponentMapper.createComponentMapping((TypeReferenceTypeSymbol) typeInclusion, components, commonData);

                RecordTypeSymbol includedRecordTypeSymbol = (RecordTypeSymbol) ((TypeReferenceTypeSymbol) typeInclusion)
                        .typeDescriptor();
                Map<String, RecordFieldSymbol> includedRecordFieldMap = includedRecordTypeSymbol.fieldDescriptors();
                for (Map.Entry<String, RecordFieldSymbol> includedRecordField : includedRecordFieldMap.entrySet()) {
                    recordFieldMap.remove(includedRecordField.getKey());
                }
            }
        }
        return allOfSchemaList;
    }

    public static Map<String, Schema> getRecordFieldsMapping(Map<String, RecordFieldSymbol> recordFieldMap,
                                                             Map<String, Schema> components, Set<String> requiredFields,
                                                             String recordName, CommonData commonData) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        for (Map.Entry<String, RecordFieldSymbol> recordField : recordFieldMap.entrySet()) {
            RecordFieldSymbol recordFieldSymbol = recordField.getValue();
            String recordFieldName = MapperCommonUtils.unescapeIdentifier(recordField.getKey().trim());
            if (!recordFieldSymbol.isOptional() && !recordFieldSymbol.hasDefaultValue()) {
                requiredFields.add(recordFieldName);
            }
            String recordFieldDescription = getRecordFieldTypeDescription(recordFieldSymbol);
            Schema recordFieldSchema = ComponentMapper.getTypeSchema(recordFieldSymbol.typeDescriptor(),
                    components, commonData);
            if (Objects.nonNull(recordFieldDescription) && Objects.nonNull(recordFieldSchema)) {
                recordFieldSchema = recordFieldSchema.description(recordFieldDescription);
            }
            if (recordFieldSymbol.hasDefaultValue()) {
                String recordFieldDefaultValue = getRecordFieldDefaultValue(recordName, recordFieldName,
                        commonData.moduleMemberVisitor());
                if (Objects.nonNull(recordFieldDefaultValue)) {
                    recordFieldSchema.setDefault(recordFieldDefaultValue);
                } else {
                    DiagnosticMessages message = DiagnosticMessages.OAS_CONVERTOR_124;
                    ExceptionDiagnostic error = new ExceptionDiagnostic(message.getCode(), message.getDescription(),
                            null, recordFieldName);
                    commonData.diagnostics().add(error);
                }
            }
            properties.put(recordFieldName, recordFieldSchema);
        }
        return properties;
    }

    public static String getRecordFieldDefaultValue(String recordName, String fieldName,
                                                    ModuleMemberVisitor moduleMemberVisitor) {
        TypeDefinitionNode recordDefNode = moduleMemberVisitor.getTypeDefinitionNode(recordName);
        if (Objects.isNull(recordDefNode)) {
            return null;
        }
        NodeList<Node> recordFields = ((RecordTypeDescriptorNode) recordDefNode.typeDescriptor()).fields();
        return recordFields.stream().filter(field -> field instanceof RecordFieldWithDefaultValueNode)
                .map(field -> (RecordFieldWithDefaultValueNode) field)
                .filter(field -> field.fieldName().toString().trim().equals(fieldName))
                .findFirst()
                .map(RecordFieldWithDefaultValueNode::expression)
                .map(Object::toString)
                .orElse(null);
    }
}
