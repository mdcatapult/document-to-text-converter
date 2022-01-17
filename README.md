# Document Library Raw Text consumer

This consumer converts various document types into raw text files using 
the Apache tika parser (as used under the hood in the
the LeadMine NER consumer).

The resulting file is stored on the filesystem and pushed back into the
document library *via* the prefetch queue.

## Execution

This is a scala application that runs inside the JVM

```bash
java -jar consumer-raw-text.jar
```

## Runtime Configuration

* **MONGO_USERNAME** - login username for mongodb
* **MONGO_PASSWORD** - login password for mongodb
* **MONGO_HOST** - host to connect to
* **MONGO_PORT** - optional: port to connect to (default: 27017) 
* **MONGO_DOCLIB_DATABASE** - database to connect to
* **MONGO_AUTHSOURCE** - optional: database to authenticate against (default: admin)
* **MONGO_DOCUMENTS_COLLECTION** - default collection to read and write to
* **RABBITMQ_USERNAME** - login username for rabbitmq
* **RABBITMQ_PASSWORD** - login password for rabbitmq
* **RABBITMQ_HOST** - host to connect to
* **RABBITMQ_PORT** - optional: port to connect to (default: 5672)
* **RABBITMQ_VHOST** - optional: vhost to connect to (default: /)
* **RABBITMQ_EXCHANGE** - optional: exchange that the consumer should be bound to
* **CONSUMER_QUEUE** - optional: name of the queue to consume (default: klein.unarchive)
* **CONSUMER_CONCURRENCY** - optional: number of messages to handle concurrently (default: 1)
* **DOWNSTREAM_QUEUE** - optional: name of queue to enqueue new files to (default: klein.prefetch)
* **DOCLIB_DERIVATIVE_PATH** - the target filesystem path for the new raw text
files (default: `/efs/derivatives/rawtext/`)

## File paths

*Example* - For a source value:

`/doclib/ebi/supplementary_data/PMC123456/example.doc`

the new text file will be created as:

`/doclib/derivatives/rawtext/ebi/supplementary_data/PMC123456/example.doc/raw.txt`

## Dependency Scanning

https://github.com/albuch/sbt-dependency-check

The sbt-dependency-check plugin can be used to create a HTML report under `target/scala-x.x/dependency-check-report.html`

```bash
sbt dependencyCheck
```
