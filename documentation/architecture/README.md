# Build the docs
## Build as HTML
```shell
docker run --rm -v $PWD:/documents asciidoctor/docker-asciidoctor   asciidoctor -r asciidoctor-diagram arc42-monteis.adoc
```
## Build as PDF
```shell
docker run --rm -v $PWD:/documents asciidoctor/docker-asciidoctor \
  asciidoctor-pdf -r asciidoctor-diagram arc42-monteis.adoc
```

This Repo contains a prebuilt [pdf](./arc42-monteis.pdf) as well as a [html-page](./arc42-monteis.html) for convenience.