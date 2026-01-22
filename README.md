# Hytale Server Status Query Protocol v1.0

## Overview
Simple TCP-based protocol for querying Hytale server player counts.

## Design Principles
- **Simplicity**: Text-based, human-readable
- **Minimal**: Only essential data (2 numbers)
- **Public**: No authentication needed
- **Fast**: Single request-response
- **Stateless**: No session management

## Protocol Specification

### Connection
- **Transport**: TCP
- **Port**: Configurable (default: 25566)
- **Encoding**: UTF-8
- **Line Ending**: `\n` (LF)

### Request Format
```
QUERY\n
```

**Fields:**
- Command: `QUERY` (uppercase)
- Terminator: Single newline character

**Example:**
```
QUERY\n
```

### Success Response Format
```
OK <current_players> <max_players>\n
```

**Fields:**
- Status: `OK` (uppercase)
- Current Players: Integer >= 0
- Max Players: Integer >= 0
- Separator: Single space character
- Terminator: Single newline character

**Example:**
```
OK 12 100\n
```

This indicates 12 current players out of 100 maximum.

### Error Response Format
```
ERROR <message>\n
```

**Fields:**
- Status: `ERROR` (uppercase)
- Message: Human-readable error description
- Separator: Single space character
- Terminator: Single newline character

**Error Conditions:**
- Empty request
- Invalid command (not "QUERY")
- Rate limit exceeded
- Internal server error

**Examples:**
```
ERROR Rate limit exceeded\n
ERROR Invalid command\n
ERROR Internal error\n
```

## Communication Flow

```
Client                          Server
  |                               |
  |--- TCP Connect -------------->|
  |                               |
  |--- "QUERY\n" ---------------->|
  |                               |
  |                         [Process]
  |                               |
  |<-- "OK 12 100\n" -------------|
  |                               |
  |--- TCP Close ---------------->|
  |                               |
```

## Timing Constraints

| Parameter | Value | Notes |
|-----------|-------|-------|
| Connection Timeout | 5 seconds | Client-side |
| Socket Timeout | 5 seconds | Both sides |
| Read Timeout | 5 seconds | Both sides |
| Max Response Time | < 100ms | Target (typical) |

## Rate Limiting

- **Default**: 120 requests per minute per IP
- **Configurable**: Via server plugin config
- **Enforcement**: Server-side
- **Response**: `ERROR Rate limit exceeded\n`

## Security Model

### Why No Authentication?
- Data is public information
- Displayed on public server list anyway
- Simplifies implementation
- Reduces operational overhead

### Protection Mechanisms
1. **Rate Limiting**: Prevents abuse/DoS
2. **Connection Limits**: Max 10 concurrent connections
3. **Timeouts**: Prevents resource exhaustion
4. **Input Validation**: Rejects malformed requests
5. **Minimal Data**: Only 2 integers exposed

### Optional Hardening
- Firewall rules (IP whitelist)
- Fail2ban integration
- DDoS protection at network layer

## Implementation Requirements

### Server (Java Plugin)
- MUST listen on configured TCP port
- MUST accept connections from any IP (unless firewalled)
- MUST respond within 5 seconds
- MUST enforce rate limiting (if enabled)
- MUST validate request format
- MUST close connection after response
- SHOULD log connection attempts
- SHOULD handle concurrent connections (up to 10)

### Client (Python/Django)
- MUST use TCP socket
- MUST send exactly "QUERY\n"
- MUST handle both OK and ERROR responses
- MUST implement 5-second timeout
- MUST close connection after receiving response
- SHOULD retry on timeout (with backoff)
- SHOULD log all queries for analytics

## Data Validation

### Server Response Validation (Client-side)
```python
# Parse response
parts = response.split()

# Check format
assert parts[0] in ['OK', 'ERROR'], "Invalid status"

if parts[0] == 'OK':
    assert len(parts) == 3, "Invalid OK format"
    current = int(parts[1])
    max_players = int(parts[2])
    
    # Sanity checks
    assert current >= 0, "Negative current players"
    assert max_players >= 0, "Negative max players"
    
    # Warning (not error) if exceeded
    if current > max_players:
        log_warning("Current exceeds max")
```

## Error Handling

### Network Errors
| Error | Meaning | Action |
|-------|---------|--------|
| Connection Refused | Server not running | Mark server offline |
| Connection Timeout | Network issue | Retry with backoff |
| DNS Error | Invalid hostname | Check server config |
| Socket Error | Network problem | Retry later |

### Protocol Errors
| Error | Meaning | Action |
|-------|---------|--------|
| Empty Response | Connection closed | Mark server offline |
| Invalid Format | Server bug or attack | Log and skip |
| Rate Limited | Too many requests | Reduce query frequency |

## Version History

**v1.0 (Current)**
- Initial protocol definition
- Text-based request/response
- No authentication
- Built-in rate limiting

## Future Considerations

**Not included in v1.0:**
- Authentication (not needed for public data)
- Compression (only 2 numbers)
- Binary protocol (unnecessary complexity)
- TLS/encryption (not needed for non-sensitive data)
- Keep-alive connections (stateless design preferred)
- MOTD or server description (outside scope)

These features were intentionally excluded to maintain simplicity.