-- Feature 005: nova ação de canal DELETE_MESSAGE (apagar a mensagem em que o professor digitou o CPF).
-- Só estende a CHECK do tipo, com o mesmo nome (ck_channel_action_type, criada na V6, renomeada na V7 e recriada na V8).
-- Aditiva: não toca nas linhas existentes; os cinco tipos anteriores continuam aceitos.
ALTER TABLE channel_action DROP CONSTRAINT ck_channel_action_type;
ALTER TABLE channel_action ADD CONSTRAINT ck_channel_action_type
    CHECK (type IN ('MESSAGE', 'EDIT', 'DOCUMENT', 'CALLBACK_REPLY', 'STEP_MESSAGE', 'DELETE_MESSAGE'));
