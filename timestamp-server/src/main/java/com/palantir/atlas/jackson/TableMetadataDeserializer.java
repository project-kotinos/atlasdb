package com.palantir.atlas.jackson;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Lists;
import com.palantir.atlasdb.protos.generated.TableMetadataPersistence.ValueByteOrder;
import com.palantir.atlasdb.table.description.ColumnMetadataDescription;
import com.palantir.atlasdb.table.description.ColumnValueDescription;
import com.palantir.atlasdb.table.description.ColumnValueDescription.Format;
import com.palantir.atlasdb.table.description.DynamicColumnDescription;
import com.palantir.atlasdb.table.description.NameComponentDescription;
import com.palantir.atlasdb.table.description.NameMetadataDescription;
import com.palantir.atlasdb.table.description.NamedColumnDescription;
import com.palantir.atlasdb.table.description.TableMetadata;
import com.palantir.atlasdb.table.description.ValueType;
import com.palantir.atlasdb.transaction.api.ConflictHandler;
import com.palantir.common.persist.Persistable;

public class TableMetadataDeserializer extends StdDeserializer<TableMetadata> {
    private static final long serialVersionUID = 1L;

    protected TableMetadataDeserializer() {
        super(TableMetadata.class);
    }

    @Override
    public TableMetadata deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = jp.readValueAsTree();
        NameMetadataDescription row = deserializeRowish(node);
        ColumnMetadataDescription col;
        if (node.get("is_dynamic").asBoolean()) {
            col = deserializeDynamicCol(node);
        } else {
            col = deserializeNamedCols(node);
        }

        return new TableMetadata(row, col, ConflictHandler.IGNORE_ALL);
    }

    private NameMetadataDescription deserializeRowish(JsonNode node) {
        Collection<NameComponentDescription> rowComponents = Lists.newArrayList();
        for (JsonNode rowNode : node.get("row")) {
            String name = rowNode.get("name").asText();
            ValueType type = ValueType.valueOf(rowNode.get("type").asText());
            ValueByteOrder order = ValueByteOrder.valueOf(rowNode.get("order").asText());
            rowComponents.add(new NameComponentDescription(name, type, order));
        }
        NameMetadataDescription row = new NameMetadataDescription(rowComponents);
        return row;
    }

    private ColumnMetadataDescription deserializeDynamicCol(JsonNode node) {
        NameMetadataDescription col = deserializeRowish(node.get("column"));
        ColumnValueDescription val = deserializeValue(node.get("value"));
        DynamicColumnDescription dynamicCol = new DynamicColumnDescription(col, val);
        return new ColumnMetadataDescription(dynamicCol);
    }

    private ColumnMetadataDescription deserializeNamedCols(JsonNode node) {
        Collection<NamedColumnDescription> cols = Lists.newArrayList();
        for (JsonNode colNode : node.get("columns")) {
            String name = colNode.get("name").asText();
            String longName = colNode.get("long_name").asText();
            ColumnValueDescription val = deserializeValue(colNode.get("value"));
            cols.add(new NamedColumnDescription(name, longName, val));
        }
        return new ColumnMetadataDescription(cols);
    }

    private ColumnValueDescription deserializeValue(JsonNode node) {
        Format format = Format.valueOf(node.get("format").asText());
        switch (format) {
        case BLOCK_STORED_PROTO:
            // This is wrong, but block stored proto isn't really supported.
            return ColumnValueDescription.forType(ValueType.BLOB);
        case PERSISTABLE:
            String className = node.get("type").asText();
            try {
                return ColumnValueDescription.forPersistable(Class.forName(className).asSubclass(Persistable.class));
            } catch (Exception e) {
                // Also wrong, but what else can you do?
                return ColumnValueDescription.forType(ValueType.BLOB);
            }
        case PROTO:
            // Not even going to bother to try.
            return ColumnValueDescription.forType(ValueType.BLOB);
        case VALUE_TYPE:
            ValueType type = ValueType.valueOf(node.get("type").asText());
            return ColumnValueDescription.forType(type);
        default:
            throw new EnumConstantNotPresentException(Format.class, format.name());
        }
    }
}
