package io.viren.orders;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpStatusCode.valueOf;

@SpringBootApplication
public class OrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }


    @Bean
    ApplicationRunner sampleDataInitializer(OrderRepository orderRepository) {
        return args -> Flux.just(Pair.of(1, "10kg Fortune Wheat Flour"), Pair.of(2, "200g Emami Bath Soap"),
                        Pair.of(3, "2kg Safeda Mango"), Pair.of(4, "250g, Bru Coffee"),
                        Pair.of(4, "500g, Nirma Detergent"),
                        Pair.of(5, "250g Tata Tea"),
                        Pair.of(6, "1kg Toor Dal"), Pair.of(7, "1/2kg Carrot"))
                .map(pair -> new Order(null, pair.getFirst(), pair.getSecond()))
                .concatMap(orderRepository::save)
                .thenMany(orderRepository.findAll())
                .subscribe();

    }

}


@RestController
class OrderController {
    OrderController(OrderRepository orderRepository, TrackingService trackingService) {
        this.orderRepository = orderRepository;
        this.trackingService = trackingService;
    }


    private final OrderRepository orderRepository;
    private final TrackingService trackingService;


    @GetMapping("/")
    Flux<Order> getAllOrders() {
        return orderRepository.findAll();
    }


    @GetMapping("/customer-orders/{customer-id}")
    Flux<OrderOut> getCustomerOrders(@PathVariable("customer-id") int customerId,
                                     @RequestParam(defaultValue = "tracking") String customizeBehaviorTargetApp,
                                     @RequestParam(value = "emulateFailure", defaultValue = "no") String signal
            , @RequestParam(value = "httpFailureCode", defaultValue = "500") String failureCode,
                                     @RequestParam(value = "emulateDelay", defaultValue = "no") String delaySignal,
                                     @RequestParam(value = "delayInMs", defaultValue = "0") String delayInMs) {
        if (!customizeBehaviorTargetApp.equalsIgnoreCase("orders")) {
            return getOrder(new CustomizeBehavior(signal, failureCode, delaySignal, delayInMs)).apply(customerId);

        }
        final var trackingOutFlux = Mono.just(delaySignal)
                .filter(ds -> ds.equalsIgnoreCase("YES"))
                .flatMapMany(i -> Mono.delay(Duration.ofMillis(Integer.parseInt(delayInMs)))
                        .thenMany(getOrder(null).apply(customerId)))
                .switchIfEmpty(Flux.defer(() -> getOrder(null).apply(customerId)));

        return Mono.just(signal)
                .filter(failureSignal -> failureSignal.equalsIgnoreCase("YES"))
                .flatMap(s -> Mono.error(new ResponseStatusException(valueOf(Integer.parseInt(failureCode)),
                        "failed on purpose at orders app.")))
                .defaultIfEmpty("neglected")
                .flatMapMany(s -> Flux.defer(() -> trackingOutFlux));
    }


    Function<Integer, Flux<OrderOut>> getOrder(CustomizeBehavior customizeBehavior) {
        return customerId -> orderRepository.findAlByCustomerId(customerId)
                .flatMap(order -> trackingService.track(order.id(), customizeBehavior)
                        .map(tracking -> new OrderOut(order.id(), order.productName(), tracking))
                        .onErrorContinue((error, object) -> getLogger(getClass()).error("Failed...!", error)));
    }


    @GetMapping("/{order-id}")
    Flux<Order> getOrderById(@PathVariable("order-id") int orderId) {
        return orderRepository.findAllById(List.of(orderId));
    }
}


@Table("CustomerOrder")
record Order(@Id Integer id, int customerId, String productName) {
}


record OrderOut(Integer orderId, String productName, Tracking tracking) {
}


@JsonInclude(NON_NULL)
record Tracking(@JsonIgnore Integer orderId, Integer trackingId, @JsonProperty("partner") String deliveryPartner,
                @JsonProperty("status") String deliveryStatus,
                LocalDate tentativeDeliveryDate) {
}


record CustomizeBehavior(String emulateFailure, String httpFailureCode, String emulateDelay, String delayInMs) {
}


interface OrderRepository extends ReactiveCrudRepository<Order, Integer> {

    Flux<Order> findAlByCustomerId(int customerId);
}


@Component
class TrackingService {

    private final WebClient webClient;
    private static final Integer timeoutInMilliseconds = 10_000;
    private static final Logger LOG = getLogger(TrackingService.class);


    TrackingService(WebClient.Builder webClientBuilder,
                    @Value("${tracking.service.base.url}") String trackingServiceBaseUrl) {
        final var connector = new ReactorClientHttpConnector(HttpClient.create()
                .option(CONNECT_TIMEOUT_MILLIS, timeoutInMilliseconds)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutInMilliseconds, MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutInMilliseconds, MILLISECONDS)))
                .compress(true));
        webClient = webClientBuilder.baseUrl(trackingServiceBaseUrl)
                .clientConnector(connector)
                .filter(new TimeoutFilter())
                .build();
    }


    Mono<Tracking> track(Integer orderId, CustomizeBehavior customizeBehavior) {
        Function<UriBuilder, URI> uriBuilder =
                builder -> builder.path("/order-tracking/{orderId}")
                        .build(orderId);
        if (null != customizeBehavior) {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("emulateFailure", customizeBehavior.emulateFailure());
            map.add("httpFailureCode", customizeBehavior.httpFailureCode());
            map.add("emulateDelay", customizeBehavior.emulateDelay());
            map.add("delayInMs", customizeBehavior.delayInMs());
            uriBuilder = builder -> builder.path("/order-tracking/{orderId}")
                    .queryParams(map)
                    .build(orderId);
        }
        return webClient.get()
                .uri(uriBuilder)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(), body))))
                .bodyToMono(Tracking.class)
                .doOnError(error -> LOG.error(error.getMessage(), error));

    }
}


class TimeoutFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
                .onErrorMap(error -> error instanceof WebClientRequestException ||
                                     error instanceof ConnectTimeoutException || error instanceof TimeoutException,
                        error -> new ResponseStatusException(HttpStatusCode.valueOf(408)));

    }
}
