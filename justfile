# Default target: list all available recipes
default:
    @just --list

# Build all Docker images
build:
    docker compose build

# Compile and install all services cleanly
compile:
    @echo "Compiling common shared library..."
    mvn clean install -f common/pom.xml -DskipTests -B
    @echo "Compiling all microservices..."
    mvn clean compile -f eureka-server/pom.xml -DskipTests -B
    mvn clean compile -f api-gateway/pom.xml -DskipTests -B
    mvn clean compile -f auth-service/pom.xml -DskipTests -B
    mvn clean compile -f user-service/pom.xml -DskipTests -B
    @echo "✅ All microservices compiled successfully!"

# Start all services
up:
    docker compose up -d

# Start with development overrides (hot reload + debug)
dev:
    docker compose -f docker-compose.yml -f docker-compose.override.yml up -d

# Start production configuration
prod:
    docker compose --profile production up -d

# Stop all services
down:
    docker compose down

# Show logs for all services
logs:
    docker compose logs -f

# Show logs for a specific service (example: just logs-svc auth-service)
logs-svc service:
    docker compose logs -f {{service}}

# Remove all containers and volumes (⚠️ This deletes all data!)
clean:
    docker compose down -v
    docker system prune -f

# Restart all services
restart: down up

# Show status of all services
status:
    docker compose ps

# Quick health check of all microservices
health:
    @echo "Checking service health..."
    @curl -s http://localhost:8761/actuator/health > /dev/null && echo "✅ Eureka Server: Healthy" || echo "❌ Eureka Server: Unhealthy"
    @curl -s http://localhost:8085/actuator/health > /dev/null && echo "✅ Auth Service: Healthy" || echo "❌ Auth Service: Unhealthy"
    @curl -s http://localhost:8084/actuator/health > /dev/null && echo "✅ User Service: Healthy" || echo "❌ User Service: Unhealthy"
    @curl -s http://localhost:8080/actuator/health > /dev/null && echo "✅ API Gateway: Healthy" || echo "❌ API Gateway: Unhealthy"

# Access URLs of all registered services
access:
    @echo "Service URLs:"
    @echo "🌐 API Gateway:             http://localhost:8080"
    @echo "🔍 Eureka Dashboard:         http://localhost:8761"
    @echo "📚 Swagger UI (Gateway):     http://localhost:8080/swagger-ui.html"
    @echo "🔐 Auth Service:             http://localhost:8085/swagger-ui.html"
    @echo "👤 User Service:             http://localhost:8084/swagger-ui.html"
    @echo "🐰 RabbitMQ Management:      http://localhost:15672 (admin/password)"

# Print database connection instructions
db-connect:
    @echo "Database Connection Commands:"
    @echo "👤 User DB:         docker exec -it user-db psql -U postgres -d user_service"

# Run quick REST integration tests
test:
    @echo "Running quick integration test..."
    @curl -s http://localhost:8085/auth/register -H "Content-Type: application/json" -d '{"username":"testuser","password":"testpass","email":"test@example.com"}' | jq . || echo "Auth service test failed"
