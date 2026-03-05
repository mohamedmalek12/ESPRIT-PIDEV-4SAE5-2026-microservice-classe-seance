# Classe-Seance Service – English School Management Platform

## Overview

This module is part of a School Management Platform designed to manage the academic activities of an English language school.

The **Classe-Seance service** is responsible for managing classes and scheduling sessions. It allows administrators to organize courses, assign sessions to classes, and maintain a structured timetable for the school.

This service helps ensure that courses are properly scheduled and that students and teachers can follow an organized learning program.

## Features

* CRUD operations for **Classes**
* CRUD operations for **Séances (Sessions)**
* Assign sessions to specific classes
* Manage course schedules
* RESTful APIs for communication with other microservices
* Integration with other modules such as room and material management

## Tech Stack

### Backend

* Spring Boot
* Java
* Spring Data JPA
* MySQL
* Maven

## Architecture

This service follows a **Microservices Architecture** and is part of a larger system composed of several independent services.

The main components include:

* **Controller Layer** – Handles HTTP requests and exposes REST APIs
* **Service Layer** – Contains business logic
* **Repository Layer** – Handles database access
* **Entity Layer** – Represents database models such as `Classe` and `Seance`

The service interacts with other modules of the platform including:

* Room and equipment management
* Student management
* School administration

## Contributors

* Med Malek Chourabi
* Project Team – 4SAE5

## Academic Context

Developed at **Esprit School of Engineering – Tunisia**
Software Engineering Program – **4th Year (4SAE5)**
Academic Project – **PIDEV**
Academic Year **2025–2026**

## Getting Started

### Prerequisites

* Java 17+
* Maven
* MySQL
* IDE (IntelliJ IDEA recommended)

### Installation

1. Clone the repository
2. Configure the database connection in `application.properties`
3. Run the Spring Boot application

Example:

```
mvn spring-boot:run
```

The service will start and exp
