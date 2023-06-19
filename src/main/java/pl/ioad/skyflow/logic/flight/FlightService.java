package pl.ioad.skyflow.logic.flight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.stereotype.Service;
import pl.ioad.skyflow.logic.flight.opensky.Credentials;
import pl.ioad.skyflow.logic.flight.opensky.OpenSkyFlight;
import pl.ioad.skyflow.logic.flight.payload.FlightSearchRequest;

import java.io.UncheckedIOException;
import java.util.List;

import static pl.ioad.skyflow.logic.flight.opensky.Connection.sendGetRequest;
import static pl.ioad.skyflow.logic.flight.opensky.Endpoint.DEPARTURE;

@Service
@RequiredArgsConstructor
public class FlightService {

    private static final String AIRPORT_CODE = "airport";
    private static final String BEGIN_TIME = "begin";
    private static final String END_TIME = "end";
    private static final int DEFAULT_CAPACITY = 30; //TODO: Temporarily hardcoded

    private final Credentials credentials;
    private final ObjectMapper mapper;

    /**
     * @param request Flight search request
     * @return {@link List}&lt;{@link OpenSkyFlight}&gt;
     */
    public List<Flight> findFlight(FlightSearchRequest request) {
        List<NameValuePair> requestParams = List.of(
                new BasicNameValuePair(AIRPORT_CODE, request.departureAirport()),
                new BasicNameValuePair(BEGIN_TIME, request.begin()),
                new BasicNameValuePair(END_TIME, request.end())
        );
        var response = sendGetRequest(DEPARTURE, credentials, requestParams);
        return mapResponseToFlight(response).stream()
                .map(openSkyFlight -> new Flight(openSkyFlight, DEFAULT_CAPACITY))
                .toList();
    }

    private List<OpenSkyFlight> mapResponseToFlight(String response) {
        try {
            return mapper.readValue(response, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

}
