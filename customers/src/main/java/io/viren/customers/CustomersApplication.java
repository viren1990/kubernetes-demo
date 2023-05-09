package io.viren.customers;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
                .subscribe(System.out::println);
    }

}


record Customer(@Id Integer id, String name) {
}


@RestController
class CustomerController {
    private final CustomerRepository customerRepository;


    CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    @GetMapping("/customers")
    Flux<Customer> getCustomers() {
        return this.customerRepository.findAll();
    }

}


@Repository
interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {

}
