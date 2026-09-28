-- CreateEnum
CREATE TYPE "ProviderKey" AS ENUM ('CLAUDE_CODE', 'CODEX', 'COMMAND_CODE', 'GEMINI');

-- CreateEnum
CREATE TYPE "ConnectionStatus" AS ENUM ('CONNECTED', 'DISCONNECTED', 'ERROR');

-- CreateEnum
CREATE TYPE "ExecutionStatus" AS ENUM ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "LogStream" AS ENUM ('STDOUT', 'STDERR', 'SYSTEM');

-- CreateTable
CREATE TABLE "ai_provider" (
    "id" TEXT NOT NULL,
    "key" "ProviderKey" NOT NULL,
    "name" TEXT NOT NULL,
    "capabilities" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ai_provider_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ai_connection" (
    "id" TEXT NOT NULL,
    "providerId" TEXT NOT NULL,
    "accountName" TEXT,
    "credentialReference" TEXT,
    "status" "ConnectionStatus" NOT NULL DEFAULT 'DISCONNECTED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ai_connection_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "agent_role" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "agent_role_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "permission_profile" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "fileRead" BOOLEAN NOT NULL DEFAULT false,
    "fileWrite" BOOLEAN NOT NULL DEFAULT false,
    "terminalExecute" BOOLEAN NOT NULL DEFAULT false,
    "gitStatus" BOOLEAN NOT NULL DEFAULT true,
    "gitDiff" BOOLEAN NOT NULL DEFAULT false,
    "gitCommit" BOOLEAN NOT NULL DEFAULT false,
    "gitPush" BOOLEAN NOT NULL DEFAULT false,
    "dbRead" BOOLEAN NOT NULL DEFAULT false,
    "dbWrite" BOOLEAN NOT NULL DEFAULT false,
    "dbSchemaChange" BOOLEAN NOT NULL DEFAULT false,
    "deploy" BOOLEAN NOT NULL DEFAULT false,
    "externalNetworkAccess" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "permission_profile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "workspace" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "workspace_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "agent" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "roleId" TEXT NOT NULL,
    "permissionProfileId" TEXT NOT NULL,
    "providerId" TEXT NOT NULL,
    "connectionId" TEXT,
    "workspaceId" TEXT,
    "model" TEXT,
    "mode" TEXT,
    "profile" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "agent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "execution" (
    "id" TEXT NOT NULL,
    "agentId" TEXT NOT NULL,
    "workspaceId" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "status" "ExecutionStatus" NOT NULL DEFAULT 'PENDING',
    "startedAt" TIMESTAMP(3),
    "finishedAt" TIMESTAMP(3),
    "exitCode" INTEGER,
    "errorMessage" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "execution_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "execution_log" (
    "id" TEXT NOT NULL,
    "executionId" TEXT NOT NULL,
    "stream" "LogStream" NOT NULL,
    "content" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "execution_log_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "ai_provider_key_key" ON "ai_provider"("key");

-- CreateIndex
CREATE UNIQUE INDEX "agent_role_name_key" ON "agent_role"("name");

-- CreateIndex
CREATE UNIQUE INDEX "permission_profile_name_key" ON "permission_profile"("name");

-- CreateIndex
CREATE UNIQUE INDEX "workspace_name_key" ON "workspace"("name");

-- CreateIndex
CREATE UNIQUE INDEX "workspace_path_key" ON "workspace"("path");

-- CreateIndex
CREATE INDEX "execution_log_executionId_idx" ON "execution_log"("executionId");

-- AddForeignKey
ALTER TABLE "ai_connection" ADD CONSTRAINT "ai_connection_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "ai_provider"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent" ADD CONSTRAINT "agent_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "agent_role"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent" ADD CONSTRAINT "agent_permissionProfileId_fkey" FOREIGN KEY ("permissionProfileId") REFERENCES "permission_profile"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent" ADD CONSTRAINT "agent_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "ai_provider"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent" ADD CONSTRAINT "agent_connectionId_fkey" FOREIGN KEY ("connectionId") REFERENCES "ai_connection"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent" ADD CONSTRAINT "agent_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "workspace"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "execution" ADD CONSTRAINT "execution_agentId_fkey" FOREIGN KEY ("agentId") REFERENCES "agent"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "execution" ADD CONSTRAINT "execution_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "workspace"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "execution_log" ADD CONSTRAINT "execution_log_executionId_fkey" FOREIGN KEY ("executionId") REFERENCES "execution"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
