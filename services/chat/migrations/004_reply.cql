-- Reply pointer on a message — the TIMEUUID of the message being quoted, or
-- empty when this message starts its own thread. We don't denormalize the
-- quoted text/sender on the server; clients resolve the pointer locally
-- (cheap, since the receiver already has full history for the conversation).

USE koto;

ALTER TABLE messages ADD reply_to TEXT;
