package com.example.hireme.equipment;

import com.example.hireme.shared.web.PageResponse;

/**
 * The filters a sentence was turned into, and the listings they match. The UI shows the filters as
 * editable chips; changing one and sending them to GET /api/v1/equipment runs the same search.
 *
 * @param interpretedBy AI when the model read the query; KEYWORDS when AI was unavailable or over the
 *                      rate limit and the query's words were matched against listings instead
 * @param notice        why the keyword fallback was used, or null
 */
public record AiSearchResponse(
        String query,
        EquipmentSearchCriteria filters,
        Interpretation interpretedBy,
        String notice,
        PageResponse<EquipmentResponse> results
) {
    public enum Interpretation {
        AI,
        KEYWORDS
    }
}
