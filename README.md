# About the project

Simple Playlist application with users, songs and playlist.

# Stack

* **Java 17**
* **Spring Boot**
* **Spring Security**
* **REST API**
* **MySQL 8**
* **Spring Data JPA**
* **Oauth2 Client**
* **JWT**
* **Swagger OpenAPI**
* **Docker**
* **Java Sender Mail**
* **Redis**

# Getting Started

This is an example of how way give instructions on setting up your project locally.

### Prerequisites
* **JDK 17** installed.
* **Docker Desktop** installed.

### Installation

Download Docker on your Operating System

* [Windows](https://docs.docker.com/desktop/setup/install/windows-install/)

* [Mac](https://docs.docker.com/desktop/setup/install/mac-install/)

Clone the repo
```bash
git clone https://github.com/zheki4-7/Playlist.git
```

### Linux

Ubuntu
```bash
sudo apt-get update
sudo apt install ./docker-desktop-amd64.deb
```

Debian
```bash
sudo apt-get update
sudo apt-get install ./docker-desktop-amd64.deb
```
Fedora
```bash
sudo dnf install ./docker-desktop-x86_64.rpm
```

Arch
```bash
wget https://download.docker.com/linux/static/stable/x86_64/docker-29.4.1.tgz -qO- | tar xvfz - docker/docker --strip-components=1
sudo cp -rp ./docker /usr/local/bin/ && rm -r ./docker
```

And download JDK 17 version

* [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

# Run program

After install run this command for build application

```bash
docker compose up
```

# Secret Key

Link for generate secret key for JWT
* [Secret Key](https://jwtsecrets.com/#generator)

Link for generate AES key
* [Generate AES key](https://www.randomkeygen.com/aes-key)

# API Documentation

Once the application is running, you can explore and test the endpoints via Swagger UI:
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)