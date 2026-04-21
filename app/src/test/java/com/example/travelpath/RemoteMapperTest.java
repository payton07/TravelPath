package com.example.travelpath;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.data.remote.RemoteMapper;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class RemoteMapperTest {

    private RemoteMapper mapper;
    private SearchCriteria criteria;

    @Before
    public void setup() {
        mapper = new RemoteMapper(new Gson());
        criteria = new SearchCriteria.Builder()
                .destinationCity("Paris")
                .interests(List.of("Culture"))
                .build();
    }

    @Test
    public void mapItineraries_correctlyParsesValidData() {
        List<Map<String, Object>> rawList = new ArrayList<>();
        Map<String, Object> itMap = new HashMap<>();
        itMap.put("name", "Paris Tour");
        itMap.put("cost", 45.5);
        itMap.put("duration", "4h");
        itMap.put("steps", "Eiffel → Louvre");
        rawList.add(itMap);

        List<Itinerary> results = mapper.mapItineraries(rawList, criteria);

        assertEquals(1, results.size());
        assertEquals("Paris Tour", results.get(0).getName());
        assertEquals(45.5, results.get(0).getCost(), 0.001);
        assertEquals("Paris", results.get(0).getDestinationCity());
    }

    @Test
    public void mapItineraries_handlesEmptyData() {
        List<Itinerary> results = mapper.mapItineraries(null, criteria);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
