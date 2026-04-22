--liquibase formatted sql
--changeset kb:012-insert-default-data

-- Insert default roles
INSERT INTO roles (name, description) VALUES
    ('ADMIN',  'Full access: manage users, spaces, documents, and settings'),
    ('EDITOR', 'Can upload, edit, and delete documents; manage tags'),
    ('VIEWER', 'Read-only access to documents and search')
ON CONFLICT (name) DO NOTHING;
