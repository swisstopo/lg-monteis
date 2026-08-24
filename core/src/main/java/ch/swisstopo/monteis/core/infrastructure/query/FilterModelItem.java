package ch.swisstopo.monteis.core.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Mirrors a single entry of ag-grid's {@code filterModel}, keyed by ag-grid's own
 * {@code filterType} discriminator. Only the filter types actually used by a column filter
 * ({@code agTextColumnFilter}, {@code agNumberColumnFilter}) are modelled; columns using other
 * filter types (date, set, ...) are not supported yet.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "filterType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextFilterModel.class, name = "text"),
  @JsonSubTypes.Type(value = NumberFilterModel.class, name = "number"),
  @JsonSubTypes.Type(value = DateFilterModel.class, name = "date")
})
public sealed interface FilterModelItem
    permits DateFilterModel, NumberFilterModel, TextFilterModel {}
