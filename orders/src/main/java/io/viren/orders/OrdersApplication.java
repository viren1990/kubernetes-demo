package io.viren.orders;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import org.springframework.http.HttpStatus;
import java.util.List;

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
class MyController {

    @GetMapping("/error")
    public ResponseEntity<String> handleRequest() {
        try {

            throw new Exception("Internal server error occurred");
        } catch (Exception e) {
            // Catch the exception and return a ResponseEntity with a 500 status code
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }
}

@RestController
class OrderController {
    OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    private final OrderRepository orderRepository;


    @GetMapping("/")
    Flux<Order> getAllOrders() {
        return orderRepository.findAll();
    }


    @GetMapping("/customers/{customer-id}")
    Flux<Order> getCustomerOrders(@PathVariable("customer-id") int customerId) {
        return orderRepository.findAlByCustomerId(customerId);
    }


    @GetMapping("/{order-id}")
    Flux<Order> getOrderById(@PathVariable("order-id") int orderId) {
        return orderRepository.findAllById(List.of(orderId));
    }
}


@Table("CustomerOrder")
record Order(@Id Integer id, int customerId, String productName) {
}


interface OrderRepository extends ReactiveCrudRepository<Order, Integer> {

    Flux<Order> findAlByCustomerId(int customerId);
}
