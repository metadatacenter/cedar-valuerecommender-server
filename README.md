# cedar-value-recommender-server

A service that provides intelligent value suggestions for metadata template field.

This project is implemented in Java using [Play Framework](http://www.playframework.com/).

The multimodule structure of this project is based on the template [play-cedar-service](https://github.com/metadatacenter/play-cedar-service),
which allows the use of Maven with the Play Framework.

The project contains two subdirectories:

- cedar-value-recommender-server-core: Core server functionality
- cedar-value-recommender-server-play: Play-based interface to server

## Versions

* Java: 1.8
* Play Framework: 2.4.6
* Elasticsearch: 2.1.2

## Getting started

Clone the project:

    $ git clone https://github.com/metadatacenter/cedar-value-recommender-server.git
    
Install Elasticsearch (using Homebrew):
    
    $ brew install elasticsearch21
    
Start Elasticsearch on the 'elasticsearch_cedar' cluster:
     
* Option 1: Update the elasticsearch.yml file (in '/usr/local/etc/elasticsearch')
    
        cluster.name: elasticsearch_cedar
    
* Option 2:
        
        $ elasticsearch --cluster.name elasticsearch_cedar
    
Install Kibana and Sense and run Kibana (optional)
    
    $ brew install kibana
    $ kibana plugin --install elastic/sense
    $ kibana

Access to Sense using the browser: http://localhost:5601/app/sense

Index some JSON template instances in Elasticsearch using the following index and type:

* Index: cedar
* Type: template_instances

## Running the tests

Go to the project root folder and execute the Maven "test" goal:

    $ mvn test

## Starting the service

At the project root folder:

    $ mvn install
    $ cd cedar-value-recommender-server-play
    $ mvn play2:run

By default, the services will be running at http://localhost:9005.

## Documentation

This project uses the Swagger Framework (http://swagger.io/), which provides interactive documentation for the terminology server. The documentation is shown when opening the default page (http://localhost:9005).

Note: The 'dist' folder from the swagger-ui project has been copied to the 'public/swagger-ui' folder and a light customization was done using the instructions provided at [https://github.com/swagger-api/swagger-ui](https://github.com/swagger-api/swagger-ui)

## IntelliJ IDEA 14 configuration

Instructions on how to configure IntelliJ 14 to build and run this project are available [here] (https://github.com/metadatacenter/cedar-docs/wiki/Maven-Play-project-configuration-in-IntelliJ-IDEA-14).

