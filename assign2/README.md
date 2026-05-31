# CPD Assignment 2 — Distributed Chat System

## Overview

This project implements a distributed client-server chat system in Java using TCP sockets.

Users connect to a central server, register or authenticate, list available chat rooms, create new rooms, join rooms and exchange text messages with other users. The system also supports AI rooms, where a Bot generates responses using a local Ollama instance.

The implementation focuses on:

- TCP-based client-server communication.
- user registration and authentication.
- session resumption using tokens.
- room creation and room membership.
- real-time message delivery.
- AI-assisted rooms backed by Ollama.
- concurrency using Java virtual threads.
- explicit synchronization using `java.util.concurrent.locks`.
- protection against slow clients.

The project was developed for the CPD 2025/2026 distributed systems assignment.

---

## Requirements

The project requires:

- Java SE 21 or newer;
- Bash-compatible shell;
- Docker, required to run the local Ollama container for AI rooms.

The normal chat functionality can be used without Ollama. Ollama is only required for AI rooms.

No external Java libraries are required. The implementation only uses Java SE APIs.

To check the Java version:

```bash
java --version
javac --version
```

The version should be 21 or higher.

---

## Project Structure

```text
assign2/
├── src/
│   └── pt/up/fe/cpd/chat/
│       ├── client/
│       │   └── ChatClient.java
│       ├── protocol/
│       │   ├── ClientCommand.java
│       │   ├── CommandParser.java
│       │   ├── CommandType.java
│       │   └── Protocol.java
│       └── server/
│           ├── AuthService.java
│           ├── ChatServer.java
│           ├── ClientConnection.java
│           ├── ClientHandler.java
│           ├── OllamaClient.java
│           ├── Room.java
│           ├── ServerState.java
│           ├── Session.java
│           ├── TokenService.java
│           ├── User.java
│           └── UserStore.java
├── compile.sh
├── server.sh
├── client.sh
├── clean.sh
└── data/
    └── users.txt
```

The `data/users.txt` file is created automatically when the server starts or when a user is registered.

---

## Compilation

From the repository root, run:

```bash
cd assign2
./compile.sh
```

This compiles all Java source files into:

```text
assign2/out
```

---

## Running the Server

After compiling, start the server with:

```bash
./server.sh
```

By default, the server listens on port `12345`.

To choose a different port:

```bash
./server.sh 5000
```

Expected output:

```text
ChatServer listening on port 12345
```

---

## Running a Client

Open another terminal and run:

```bash
./client.sh
```

By default, the client connects to:

```text
localhost:12345
```

To specify host and port:

```bash
./client.sh localhost 5000
```

Multiple clients can be started in different terminals.

---

## Cleaning Build Files

To remove compiled files:

```bash
./clean.sh
```

This removes the `assign2/out` directory.

---

## Build and run without scripts

From the `assign2` folder, compile the project manually:

```bash
mkdir -p out
javac --release 21 -d out $(find src -name "*.java")
```

Run the server from the repository root:

```bash
java -cp assign2/out pt.up.fe.cpd.chat.server.ChatServer 12345
```

Run a client from the repository root:

```bash
java -cp assign2/out pt.up.fe.cpd.chat.client.ChatClient localhost 12345
```

---

## Running Ollama for AI rooms

AI rooms use Ollama by default at:

```text
http://localhost:11434
```

The default model is:

```text
llama3:latest
```

Ollama should be run through Docker using the official `ollama/ollama` image.

Start the Ollama container:

```bash
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama
```

Run the `llama3` model inside the container:

```bash
sudo docker exec -it ollama14 ollama run llama3
```

Test the REST API:

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "llama3",
  "prompt": "How are you today?",
  "stream": false
}'
```

If Ollama is not running, the chat server does not crash. It sends a system message to the room saying that the AI service is unavailable.

---

## Client Commands

When a client starts, it prints the list of available commands.

### Register a new user

```text
REGISTER <username> <password>
```

Example:

```text
REGISTER alice password123
```

### Login

```text
LOGIN <username> <password>
```

Example:

```text
LOGIN alice password123
```

After a successful login, the server sends a session token to the client. The client stores this token in memory and uses it automatically if the TCP connection breaks and needs to be resumed.

### List rooms

```text
LIST_ROOMS
```

### Create a normal room

```text
CREATE_ROOM <room>
```

Example:

```text
CREATE_ROOM library
```

### Create an AI room

```text
CREATE_AI_ROOM <room> | <prompt>
```

Example:

```text
CREATE_AI_ROOM ai_doodle | Summarize the users' availability and suggest a meeting time.
```

Room names should not contain spaces.

### Join a room

```text
JOIN <room>
```

Example:

```text
JOIN library
```

### Leave the current room

```text
LEAVE
```

### Send a message

There are two ways to send messages.

Explicit command:

```text
MSG Hello everyone!
```

Or, after joining a room, type directly:

```text
Hello everyone!
```

### Show help

```text
HELP
```

or:

```text
/help
```

### Quit

```text
QUIT
```

or:

```text
/quit
```

---

## Example Session

Terminal 1:

```bash
cd assign2
./compile.sh
./server.sh
```

Terminal 2:

```bash
cd assign2
./client.sh
```

Client 1:

```text
REGISTER alice pass
LOGIN alice pass
CREATE_ROOM library
JOIN library
MSG Hi people! Anyone?
```

Terminal 3:

```bash
cd assign2
./client.sh
```

Client 2:

```text
REGISTER eve pass
LOGIN eve pass
JOIN library
MSG I am giving free apples.
```

The clients will receive room messages in the following style:

```text
[library] Alice: Hi people! Anyone?
[library] Eve: I am giving free apples.
```

---

## Example with an AI room

Create an AI room:

```text
CREATE_AI_ROOM airoom | Summarize the conversation briefly.
```

Join the room:

```text
JOIN airoom
```

Send messages:

```text
Alice is available on Monday
Bob is available on Tuesday
```

In an AI room, every user message sends the room prompt and the room message history to Ollama.

The AI response is sent back to the room using the special user name `Bot`.

Example output:

```text
[airoom] Bot: Alice is available on Monday and Bob is available on Tuesday.
```

---

## Implementation Details

### TCP Communication

The server uses a `ServerSocket` to listen for incoming TCP connections. For each accepted client connection, the server starts a new virtual thread running a `ClientHandler`.

The client uses a TCP `Socket` to connect to the server.

### Protocol

The protocol is based on simple text commands sent by the client:

```text
REGISTER
LOGIN
RESUME
LIST_ROOMS
CREATE_ROOM
CREATE_AI_ROOM
JOIN
LEAVE
MSG
QUIT
```

Server responses include:

```text
OK ...
ERROR ...
TOKEN ...
ROOMS ...
ROOM_MESSAGE ...
SYSTEM ...
```

This makes the protocol easy to test manually and more compact.

### Authentication and User Storage

Users can register with a username and password. Passwords are hashed with SHA-256 before being stored.

The registered users are persisted in:

```text
assign2/data/users.txt
```

Each line contains:

```text
username;passwordHash
```

This allows users to remain registered after restarting the server.

### Sessions and Tokens

After login, the server creates a session token and sends it to the client.

The client does not store the username and password for reconnection. Instead, it stores the token in memory and uses:

```text
RESUME <token>
```

if the TCP connection breaks.

Tokens expire after 30 minutes.

### Fault Tolerance

The project supports automatic reconnection after a broken TCP connection.

After login, the client stores the session token in memory.

If a connection is lost, the client automatically tries to reconnect. If it still has a valid token, it sends a `RESUME` command to the server. The server then binds the new TCP connection to the existing user session.

If the user was inside a room before the disconnection, the session still remembers the current room, so the user can continue receiving messages after reconnection.

### Concurrency

The server uses Java virtual threads to reduce thread overhead. Each client connection is handled independently.

Shared server state is stored in `ServerState` and protected with a `ReentrantLock`. The implementation uses regular Java collections such as `HashMap`, `HashSet` and `ArrayList`, and protects access to them explicitly with locks.

This avoids relying on thread-safe collection implementations from `java.util.concurrent`.

### Slow Clients

Each active client connection has its own outgoing message queue.

Messages are added to the queue by the server and written by a separate virtual writer thread. This prevents a slow client from blocking the whole server or delaying message delivery to other users.

If a client queue grows beyond the configured limit, the server considers that client too slow and disconnects it.

### Rooms

A room contains:

- a room name;
- the current members;
- a message history;
- optionally, an AI prompt.

Normal rooms simply broadcast user and system messages.

AI rooms additionally trigger a request to Ollama whenever a user sends a message.

### AI Bot

In an AI room, the server builds a prompt containing:

- the room name;
- the room prompt;
- the previous room messages;
- the latest user message.

The response from Ollama is broadcast to the room as a message from ```bot```

If Ollama is unavailable, the server sends a system message warning users that the AI service is unavailable.

## Main files

| File | Purpose |
|---|---|
| `ChatServer.java` | Starts the TCP server and accepts client sockets |
| `ChatClient.java` | Console client with input handling, output handling, reconnect, and token resume |
| `ClientHandler.java` | Handles commands received from one client connection |
| `ServerState.java` | Stores users, sessions, rooms, and protects shared state with a lock |
| `ClientConnection.java` | Manages one client socket and its outgoing message queue |
| `Session.java` | Stores username, token, token expiration, current room, and current connection |
| `Room.java` | Stores room name, members, message history, and optional AI prompt |
| `User.java` | Represents a registered user |
| `UserStore.java` | Loads and saves registered users in a text file |
| `AuthService.java` | Hashes and verifies passwords |
| `TokenService.java` | Creates session tokens with expiration time |
| `OllamaClient.java` | Sends prompts to the local Ollama REST API |
| `CommandParser.java` | Parses client commands |
| `ClientCommand.java` | Stores a parsed command type and its arguments |
| `CommandType.java` | Lists supported command types |
| `Protocol.java` | Builds protocol messages sent between server and client |

## Important methods

| Method | Purpose |
|---|---|
| `ChatServer.start` | Opens the server socket and accepts clients |
| `ClientHandler.handleLogin` | Verifies credentials and creates a session |
| `ClientHandler.handleResume` | Restores a session from a token |
| `ClientHandler.handleCreateRoom` | Creates a normal room |
| `ClientHandler.handleCreateAiRoom` | Creates an AI room with a prompt |
| `ClientHandler.handleMessageText` | Sends a user message to the current room |
| `ClientHandler.triggerAiResponse` | Starts an Ollama request for an AI room |
| `ServerState.registerUser` | Registers and persists a new user |
| `ServerState.storeSession` | Stores a session and replaces older sessions for the same user |
| `ServerState.findValidSession` | Finds a session by token and removes expired sessions |
| `ServerState.joinRoom` | Adds a session to a room |
| `ServerState.leaveRoom` | Removes a session from the current room |
| `ServerState.broadcastRoomMessage` | Stores and sends a user message to room members |
| `ServerState.broadcastBotMessage` | Stores and sends a Bot message to room members |
| `ClientConnection.send` | Adds a message to the outgoing queue |
| `ChatClient.runConnectionManager` | Manages connection and reconnect attempts |
| `ChatClient.performResumeHandshake` | Sends the resume token after reconnecting |
| `ChatClient.readConsole` | Reads user input and handles local commands |
| `OllamaClient.generate` | Sends a prompt to Ollama and returns the response |


---

## Notes

- Room names should not contain spaces. Use names such as `general`, `library` or `ai_doodle`.
- Tokens are kept in memory, so active sessions are lost if the server process restarts.
- Room messages are kept in memory and are not persisted to disk.
- AI rooms depend on a local Ollama server being available.

---
