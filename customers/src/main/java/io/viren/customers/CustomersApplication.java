package io.viren.customers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.Optional;
import java.util.function.Function;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.http.HttpStatusCode.valueOf;
import static reactor.core.publisher.Mono.error;

@SpringBootApplication
public class CustomersApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersApplication.class, args);
    }


    @Bean
    ApplicationRunner sampleDataInitializer(CustomerRepository customerRepository) {
        return args -> Flux.just("Aakash", "Viren", "Anusha", "Hari", "Suriya", "Sajeev", "Sameer")
                .map(name -> new Customer(null, name))
                .concatMap(customerRepository::save)
                .thenMany(customerRepository.findAll())
                .subscribe();
    }

}


record Customer(@Id Integer id, String name) {
}


@RestController
class CustomerController {
    private final CustomerRepository customerRepository;
    private final OrderService orderService;

    private final Logger log = LoggerFactory.getLogger(CustomerController.class);


    CustomerController(CustomerRepository customerRepository, OrderService orderService) {
        this.customerRepository = customerRepository;
        this.orderService = orderService;
    }


    @GetMapping("/customers")
    Flux<Customer> getCustomers() {
        return this.customerRepository.findAll();
    }


    @PostMapping(value = {"/error/{http-code}", "/error"})
    Mono<Void> createFailure(@PathVariable("http-code") Optional<String> httpCode) {
        return httpCode.map(code -> Mono.error(new ResponseStatusException(valueOf(Integer.parseInt(code)))))
                .orElse(Mono.error(new ResponseStatusException(valueOf(500))))
                .then();
    }


    @GetMapping("/customers/{customer-id}")
    Mono<CustomerResponseDto> getCustomerById(@PathVariable("customer-id") int customerId,
                                              @RequestParam(defaultValue = "tracking")
                                              String customizeBehaviorTargetApp,
                                              @RequestParam(value = "emulateFailure", defaultValue = "no") String signal
            , @RequestParam(value = "httpFailureCode", defaultValue = "500") String failureCode,
                                              @RequestParam(value = "emulateDelay", defaultValue = "no")
                                              String delaySignal,
                                              @RequestParam(value = "delayInMs", defaultValue = "0") String delayInMs) {
        if (!customizeBehaviorTargetApp.equalsIgnoreCase("customers")) {
            return getCustomer(new CustomizeBehavior(signal, failureCode, delaySignal, delayInMs,
                    customizeBehaviorTargetApp)).apply(customerId);

        }
        final var customerOutMono = Mono.just(delaySignal)
                .filter(ds -> ds.equalsIgnoreCase("YES"))
                .flatMap(i -> Mono.delay(Duration.ofMillis(Integer.parseInt(delayInMs)))
                        .then(getCustomer(null).apply(customerId)))
                .switchIfEmpty(Mono.defer(() -> getCustomer(null).apply(customerId)));

        return Mono.just(signal)
                .filter(failureSignal -> failureSignal.equalsIgnoreCase("YES"))
                .flatMap(s -> Mono.error(new ResponseStatusException(valueOf(Integer.parseInt(failureCode)),
                        "failed on purpose at customers app")))
                .defaultIfEmpty("neglected")
                .flatMap(s -> Mono.defer(() -> customerOutMono));

    }


    private Function<Integer, Mono<CustomerResponseDto>> getCustomer(CustomizeBehavior customizeBehavior) {
        return customerId -> Mono.defer(() -> this.customerRepository.findById(customerId)
                .switchIfEmpty(error(new ResponseStatusException(valueOf(400), "Supply valid customer id.")))
                .flatMap(customer -> this.orderService.getCustomerOrders(customerId, customizeBehavior)
                        .collectList()
                        .map(orders -> new CustomerResponseDto(customer, orders))
                        .doOnError(error -> log.error(error.getMessage(), error))));
    }

}


interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {

}


record CustomizeBehavior(String emulateFailure, String httpFailureCode, String emulateDelay, String delayInMs,
                         String customBehaviorTargetApp) {
}


@JsonInclude(NON_NULL)
record CustomerOrderView(int orderId, @JsonIgnore int customerId, String productName,
                         Tracking tracking) {
}


record CustomerResponseDto(Customer customer, List<CustomerOrderView> orders) {
}


@JsonInclude(NON_NULL)
record Tracking(@JsonIgnore Integer orderId, Integer trackingId, @JsonProperty("partner") String deliveryPartner,
                @JsonProperty("status") String deliveryStatus,
                LocalDate tentativeDeliveryDate) {
}


@Service
class OrderService {
    private final WebClient webClient;
    private static final Integer timeoutInMilliseconds = 12_000;


    OrderService(WebClient.Builder webClientBuilder, @Value("${orders.service.base-path}") String serviceBasePath) {
        final var connector = new ReactorClientHttpConnector(HttpClient.create()
                .option(CONNECT_TIMEOUT_MILLIS, timeoutInMilliseconds)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutInMilliseconds, MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutInMilliseconds, MILLISECONDS)))
                .compress(true));
        webClient = webClientBuilder.baseUrl(serviceBasePath)
                .clientConnector(connector)
                .filter(new TimeoutFilter())
                .build();
    }


    Flux<CustomerOrderView> getCustomerOrders(final int customerId, CustomizeBehavior customizeBehavior) {
        Function<UriBuilder, URI> uriBuilder =
                builder -> builder.path("/customer-orders/{customer-id}")
                        .build(customerId);
        if (null != customizeBehavior) {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("emulateFailure", customizeBehavior.emulateFailure());
            map.add("httpFailureCode", customizeBehavior.httpFailureCode());
            map.add("emulateDelay", customizeBehavior.emulateDelay());
            map.add("delayInMs", customizeBehavior.delayInMs());
            map.add("customizeBehaviorTargetApp", customizeBehavior.customBehaviorTargetApp());
            uriBuilder = builder -> builder.path("/customer-orders/{customer-id}")
                    .queryParams(map)
                    .build(customerId);
        }
        return this.webClient.get()
                .uri(uriBuilder)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(), body))))
                .bodyToFlux(CustomerOrderView.class);
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
