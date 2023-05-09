create table CustomerOrder
(
    id           serial primary key,
    customer_id int not null,
    product_name varchar(255) not null
);
