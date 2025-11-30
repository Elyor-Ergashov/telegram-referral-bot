# Telegram Referral Bot

A powerful Telegram bot built with **Java 17** and **Spring Boot 3** that implements a complete referral system with rewards.

## Features

✅ **User Registration** - Automatic user registration with unique referral codes  
✅ **Referral System** - Track referrals and reward users who reach milestones  
✅ **Private Channel Access** - Users get invite link after 5 successful referrals  
✅ **Admin Commands** - Broadcast messages and view statistics  
✅ **Dual Mode Support** - Long Polling or Webhook mode  
✅ **PostgreSQL/H2** - Production-ready database with development mode  

## Tech Stack

- **Java 17**
- **Spring Boot 3.1.5**
- **Spring Data JPA**
- **PostgreSQL** (production) / **H2** (development)
- **Telegram Bots API 6.8.0**
- **Lombok**
- **Maven**

## Database Schema

### User Entity
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    referral_code VARCHAR(50) NOT NULL UNIQUE,
    invited_by_user_id BIGINT,
    referral_count INTEGER NOT NULL DEFAULT 0,
    has_received_invite_link BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_id ON users(chat_id);
CREATE INDEX idx_referral_code ON users(referral_code);
```

## Prerequisites

1. **Java 17** or higher
2. **Maven 3.6+**
3. **PostgreSQL** (or use H2 for development)
4. **Telegram Bot Token** - Get from [@BotFather](https://t.me/BotFather)
5. **Private Channel** - Create a private Telegram channel and get invite link

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ClientBot
```

### 2. Configure Database

**For Development (H2):**
- No configuration needed
- Uses in-memory H2 database
- Run with `-Dspring.profiles.active=dev`

**For Production (PostgreSQL):**
```bash
# Create database
createdb telegram_bot

# Or use psql
psql -U postgres
CREATE DATABASE telegram_bot;
```

### 3. Configure Bot Settings

Create a file `.env` or set environment variables:

```bash
# Required
export TELEGRAM_BOT_TOKEN="your_bot_token_from_botfather"
export TELEGRAM_BOT_USERNAME="your_bot_username"
export PRIVATE_CHANNEL_LINK="https://t.me/+your_private_channel_invite_link"

# Optional
export ADMIN_CHAT_IDS="123456789,987654321"
```

Or edit `src/main/resources/application.yml`:

```yaml
telegram:
  bot:
    token: YOUR_BOT_TOKEN_HERE
    username: YOUR_BOT_USERNAME
    mode: polling  # or webhook
  privateChannel:
    inviteLink: https://t.me/+YOUR_PRIVATE_CHANNEL_INVITE_LINK

bot:
  admin:
    chatIds:
      - 123456789  # Your Telegram chat ID
```

### 4. Build and Run

**Development Mode (H2):**
```bash
mvn clean package
java -jar target/telegram-referral-bot-1.0.0.jar --spring.profiles.active=dev
```

**Production Mode (PostgreSQL):**
```bash
mvn clean package
java -jar target/telegram-referral-bot-1.0.0.jar
```

**Or use Maven Spring Boot plugin:**
```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production
mvn spring-boot:run
```

## Bot Commands

### User Commands

| Command | Description |
|---------|-------------|
| `/start` | Register and get your referral link |
| `/start <code>` | Join using a referral code |
| `/stats` | View your referral statistics |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/admin_broadcast <message>` | Send message to all users |
| `/admin_stats` | View bot statistics |

## How It Works

### 1. User Registration
- User sends `/start` to the bot
- Bot creates user account with unique referral code
- Returns personal referral link: `https://t.me/BOT_USERNAME?start=REFERRAL_CODE`

### 2. Referral Process
- New user clicks referral link → `/start REFERRAL_CODE`
- Bot links new user to inviter
- Inviter's referral count increases
- Both users are notified

### 3. Reward System
- When user reaches 5 referrals
- Bot automatically sends private channel invite link
- Link is sent only once per user

## Configuration Modes

### Long Polling Mode (Default)

```yaml
telegram:
  bot:
    mode: polling
```

✅ Easy setup  
✅ Works behind NAT/firewall  
✅ No SSL certificate needed  
❌ Higher latency  

### Webhook Mode

```yaml
telegram:
  bot:
    mode: webhook
  webhook:
    url: https://yourdomain.com
    path: /webhook
```

✅ Lower latency  
✅ Better for production  
❌ Requires public HTTPS URL  
❌ Needs SSL certificate  

#### Setting Up Webhook

1. Deploy bot to server with public domain
2. Configure webhook URL in `application.yml`
3. Bot will automatically set webhook on startup

**Manual webhook setup:**
```bash
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://yourdomain.com/webhook"}'
```

**Check webhook status:**
```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

## Getting Your Telegram Chat ID

1. Send `/start` to [@userinfobot](https://t.me/userinfobot)
2. Bot will reply with your user ID
3. Add this ID to `bot.admin.chatIds` in configuration

## Project Structure

```
ClientBot/
├── src/main/java/com/muzaffar/telegram/
│   ├── TelegramBotApplication.java          # Main application
│   ├── bot/
│   │   ├── TelegramBot.java                 # Long polling implementation
│   │   └── TelegramWebhookBot.java          # Webhook implementation
│   ├── config/
│   │   ├── AppConfig.java                   # App configuration
│   │   ├── BotConfig.java                   # Bot configuration
│   │   └── TelegramBotConfiguration.java    # Bot initialization
│   ├── controller/
│   │   └── WebhookController.java           # Webhook endpoint
│   ├── entity/
│   │   └── User.java                        # User entity
│   ├── repository/
│   │   └── UserRepository.java              # Data access
│   └── service/
│       ├── MessageService.java              # Message formatting
│       └── UserService.java                 # Business logic
└── src/main/resources/
    ├── application.yml                       # Main configuration
    └── application-dev.yml                   # Dev profile
```

## Troubleshooting

### Bot doesn't respond
- Check bot token is correct
- Verify bot is running (check logs)
- Ensure database connection is working
- For webhook: verify URL is accessible

### Database errors
- Check PostgreSQL is running: `pg_isready`
- Verify database exists: `psql -l`
- Check credentials in `application.yml`

### Referral not working
- Check referral code format
- Verify user isn't using their own code
- Check logs for error messages

## Development

### Running Tests
```bash
mvn test
```

### Building Without Tests
```bash
mvn clean package -DskipTests
```

### Viewing H2 Console (Dev Mode)
When running in dev mode, access H2 console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: *(empty)*

## Production Deployment

### Using Docker (Optional)

Create `Dockerfile`:
```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/telegram-referral-bot-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t telegram-bot .
docker run -p 8080:8080 \
  -e TELEGRAM_BOT_TOKEN=your_token \
  -e TELEGRAM_BOT_USERNAME=your_username \
  -e PRIVATE_CHANNEL_LINK=your_link \
  telegram-bot
```

### Using systemd (Linux)

Create `/etc/systemd/system/telegram-bot.service`:
```ini
[Unit]
Description=Telegram Referral Bot
After=network.target postgresql.service

[Service]
Type=simple
User=bot
WorkingDirectory=/opt/telegram-bot
ExecStart=/usr/bin/java -jar telegram-referral-bot-1.0.0.jar
Restart=on-failure
RestartSec=10

Environment="TELEGRAM_BOT_TOKEN=your_token"
Environment="TELEGRAM_BOT_USERNAME=your_username"
Environment="PRIVATE_CHANNEL_LINK=your_link"

[Install]
WantedBy=multi-user.target
```

Start service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable telegram-bot
sudo systemctl start telegram-bot
sudo systemctl status telegram-bot
```

## Security Best Practices

1. ✅ Never commit bot token to version control
2. ✅ Use environment variables for sensitive data
3. ✅ Restrict admin commands to authorized users
4. ✅ Use HTTPS for webhook mode
5. ✅ Regularly update dependencies
6. ✅ Monitor logs for suspicious activity

## License

This project is provided as-is for educational and commercial use.

## Support

For issues and questions:
1. Check logs for error messages
2. Review configuration settings
3. Verify Telegram API status
4. Check database connectivity

## Credits

Built with ❤️ using:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [TelegramBots Java Library](https://github.com/rubenlagus/TelegramBots)
- [PostgreSQL](https://www.postgresql.org/)

---

**Happy Coding! 🚀**
