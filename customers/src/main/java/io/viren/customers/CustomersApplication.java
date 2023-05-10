package io.viren.customers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.handler.timeout.ReadTimeoutHandler;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.List;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.http.HttpStatusCode.valueOf;

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

    @GetMapping("/health")
    void healthEndpoint(){

    }


    @GetMapping("/customers")
    Flux<Customer> getCustomers() {
        return this.customerRepository.findAll();
    }


    @GetMapping("/customers/{customer-id}")
    Mono<CustomerResponseDto> getCustomerById(@PathVariable("customer-id") int customerId) {
        return this.orderService.getCustomerOrders(customerId)
                .collectList()
                .flatMap(orders -> this.customerRepository.findById(customerId)
                        .map(customer -> new CustomerResponseDto(customer, orders)))
                .doOnError(error -> log.error(error.getMessage(), error))
                .onErrorResume(error -> Mono.error(new ResponseStatusException(valueOf(500), "unexpected failure.")));

    }

}


interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {

}


record CustomerOrderView(@JsonProperty("id") int orderId, @JsonIgnore int customerId, String productName) {
}


record CustomerResponseDto(Customer customer, List<CustomerOrderView> orders) {
}


@Service
class OrderService {
    private final WebClient webClient;
    private static final Integer timeoutInMilliseconds = 1000;


    OrderService(WebClient.Builder webClientBuilder, @Value("${orders.service.base-path}") String serviceBasePath) {
        final var connector = new ReactorClientHttpConnector(HttpClient.create()
                .option(CONNECT_TIMEOUT_MILLIS, timeoutInMilliseconds)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutInMilliseconds, MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutInMilliseconds, MILLISECONDS)))
                .compress(true));
        webClient = webClientBuilder.baseUrl(serviceBasePath)
                .clientConnector(connector)
                .build();
    }


    Flux<CustomerOrderView> getCustomerOrders(final int customerId) {
        return this.webClient.get()
                .uri("/customers/{customer-id}", customerId)
                .retrieve()
                .bodyToFlux(CustomerOrderView.class);
    }

}
