package com.example.trackingservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static com.example.trackingservice.DeliveryPartner.FEDEX;
import static com.example.trackingservice.DeliveryStatus.DELIVERED;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.springframework.http.HttpStatusCode.valueOf;

@SpringBootApplication
public class TrackingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingServiceApplication.class, args);
    }


    private final Random random = new Random();


    @Bean
    Function<Integer, Mono<TrackingOut>> tracking() {
        final var values = List.of(DeliveryStatus.values());
        return orderId -> Mono.just(new TrackingOut(orderId, FEDEX, values.get(random.nextInt(values.size()))));
    }

}


@RestController
class TrackingController {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingController.class);

    private final Function<Integer, Mono<TrackingOut>> trackingFunc;


    TrackingController(Function<Integer, Mono<TrackingOut>> trackingFunc) {
        this.trackingFunc = trackingFunc;
    }


    @GetMapping("/order-tracking/{order-id}")
    Mono<TrackingOut> track(@PathVariable("order-id") Integer orderId,
                            @RequestParam(value = "emulateFailure", defaultValue = "no") String signal
            , @RequestParam(value = "httpFailureCode", defaultValue = "500") String failureCode,
                            @RequestParam(value = "emulateDelay", defaultValue = "no") String delaySignal,
                            @RequestParam(value = "delayInMs", defaultValue = "0") String delayInMs) {

        final var trackingOutMono = Mono.just(delaySignal)
                .filter(ds -> ds.equalsIgnoreCase("YES"))
                .flatMap(i -> Mono.delay(Duration.ofMillis(Integer.parseInt(delayInMs)))
                        .then(trackingFunc.apply(orderId)))
                .switchIfEmpty(Mono.defer(() -> trackingFunc.apply(orderId)));

        return Mono.just(signal)
                .filter(failureSignal -> failureSignal.equalsIgnoreCase("YES"))
                .flatMap(s -> Mono.error(new ResponseStatusException(valueOf(Integer.parseInt(failureCode)),
                        "failed on purpose at tracking app.")))
                .defaultIfEmpty("neglected")
                .flatMap(s -> Mono.defer(() -> trackingOutMono))
                .doOnError(error -> LOG.error(error.getMessage(), error));
    }
}


@JsonInclude(NON_NULL)
record TrackingOut(Integer orderId, Integer trackingId, DeliveryPartner partner, DeliveryStatus status,
                   LocalDate tentativeDeliveryDate) {
    static Random random = new Random();
    static final List<Integer> TRACKING_IDS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);


    TrackingOut(Integer orderId, DeliveryPartner deliveryPartner, DeliveryStatus status) {
        this(orderId, TRACKING_IDS.get(random.nextInt(TRACKING_IDS.size())), deliveryPartner, status,
                DELIVERED == status
                        ? null :
                        LocalDate.now());
    }
}


enum DeliveryPartner {FEDEX, DHL}


enum DeliveryStatus {DISPATCHED, DELIVERED}
