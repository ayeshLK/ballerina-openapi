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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ConstantSymbol;
import io.ballerina.compiler.api.symbols.EnumSymbol;
import io.ballerina.compiler.api.symbols.SingletonTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UnionTypeMapper extends TypeMapper {

    private final boolean isEnumType;

    public UnionTypeMapper(TypeReferenceTypeSymbol typeSymbol, SemanticModel semanticModel) {
        super(typeSymbol, semanticModel);
        this.isEnumType = isEnumTypeDefinition(typeSymbol);
    }

    @Override
    public Schema getReferenceTypeSchema(Map<String, Schema> components) {
        Schema schema;
        if (isEnumType) {
            schema = getEnumTypeSchema((EnumSymbol) typeSymbol.definition());
        } else {
            UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) typeSymbol.typeDescriptor();
            schema = getSchema(unionTypeSymbol, components, semanticModel);
        }
        return Objects.nonNull(schema) ? schema.description(description) : null;
    }

    public static Schema getSchema(UnionTypeSymbol typeSymbol, Map<String, Schema> components,
                                   SemanticModel semanticModel) {
        if (isUnionOfSingletons(typeSymbol)) {
            return getSingletonUnionTypeSchema(typeSymbol);
        }
        List<TypeSymbol> memberTypeSymbols = typeSymbol.memberTypeDescriptors();
        List<Schema> memberSchemas = new ArrayList<>();
        boolean nullable = hasNilableType(typeSymbol);
        for (TypeSymbol memberTypeSymbol : memberTypeSymbols) {
            if (memberTypeSymbol.typeKind().equals(TypeDescKind.NIL)) {
                continue;
            }
            Schema schema = TypeSchemaGenerator.getTypeSchema(memberTypeSymbol, components, semanticModel);
            if (Objects.nonNull(schema)) {
                memberSchemas.add(schema);
            }
        }
        if (memberSchemas.isEmpty()) {
            return null;
        }
        Schema schema;
        if (memberSchemas.size() == 1) {
            schema = memberSchemas.get(0);
            if (Objects.nonNull(schema.get$ref())) {
                schema = new ComposedSchema().allOf(List.of(schema));
            }
        } else {
            schema = new ComposedSchema().oneOf(memberSchemas);
        }
        if (nullable) {
            schema.setNullable(true);
        }
        return schema;
    }

    static boolean isUnionOfSingletons(UnionTypeSymbol typeSymbol) {
        List<TypeSymbol> memberTypeSymbols = typeSymbol.memberTypeDescriptors();
        return memberTypeSymbols.stream().allMatch(symbol -> symbol.typeKind().equals(TypeDescKind.SINGLETON) ||
                symbol.typeKind().equals(TypeDescKind.NIL));
    }

    public static boolean hasNilableType(TypeSymbol typeSymbol) {
        return switch (typeSymbol.typeKind()) {
            case TYPE_REFERENCE -> hasNilableType(((TypeReferenceTypeSymbol) typeSymbol).typeDescriptor());
            case UNION -> {
                List<TypeSymbol> memberTypeSymbols = ((UnionTypeSymbol) typeSymbol).memberTypeDescriptors();
                yield memberTypeSymbols.stream().anyMatch(symbol -> symbol.typeKind().equals(TypeDescKind.NIL));
            }
            default -> false;
        };
    }

    static Schema getSingletonUnionTypeSchema(UnionTypeSymbol typeSymbol) {
        List<TypeSymbol> memberTypeSymbols = typeSymbol.memberTypeDescriptors();
        List<String> enumValues = new ArrayList<>();
        boolean nullable = hasNilableType(typeSymbol);
        for (TypeSymbol memberTypeSymbol : memberTypeSymbols) {
            if (memberTypeSymbol.typeKind().equals(TypeDescKind.NIL)) {
                continue;
            } else if (!((SingletonTypeSymbol) memberTypeSymbol).originalType().
                    typeKind().equals(TypeDescKind.STRING)) {
                return null;
            }
            String signature = memberTypeSymbol.signature();
            enumValues.add(signature.substring(1, signature.length() - 1));
        }
        StringSchema schema = new StringSchema();
        schema.setEnum(enumValues);
        if (nullable) {
            schema.setNullable(true);
        }
        return schema;
    }

    static boolean isEnumTypeDefinition(TypeReferenceTypeSymbol typeSymbol) {
        Symbol definitionSymbol = typeSymbol.definition();
        return definitionSymbol.kind().equals(SymbolKind.ENUM);
    }

    static Schema getEnumTypeSchema(EnumSymbol typeSymbol) {
        List<ConstantSymbol> enumConstants = typeSymbol.members();
        List<String> enumValues = new ArrayList<>();
        for (ConstantSymbol constantSymbol : enumConstants) {
            Object enumValue = constantSymbol.constValue();
            enumValues.add(enumValue.toString());
        }
        return new StringSchema()._enum(enumValues);
    }
}
