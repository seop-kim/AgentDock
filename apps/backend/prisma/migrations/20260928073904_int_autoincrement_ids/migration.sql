/*
  Warnings:

  - The primary key for the `agent` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `agent` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The `connectionId` column on the `agent` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The `workspaceId` column on the `agent` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `agent_role` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `agent_role` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `ai_connection` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `ai_connection` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `ai_provider` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `ai_provider` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `execution` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `execution` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `execution_log` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `execution_log` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `permission_profile` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `permission_profile` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The primary key for the `workspace` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `id` column on the `workspace` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - Changed the type of `roleId` on the `agent` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `permissionProfileId` on the `agent` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `providerId` on the `agent` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `providerId` on the `ai_connection` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `agentId` on the `execution` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `workspaceId` on the `execution` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `executionId` on the `execution_log` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.

*/
-- DropForeignKey
ALTER TABLE "agent" DROP CONSTRAINT "agent_connectionId_fkey";

-- DropForeignKey
ALTER TABLE "agent" DROP CONSTRAINT "agent_permissionProfileId_fkey";

-- DropForeignKey
ALTER TABLE "agent" DROP CONSTRAINT "agent_providerId_fkey";

-- DropForeignKey
ALTER TABLE "agent" DROP CONSTRAINT "agent_roleId_fkey";

-- DropForeignKey
ALTER TABLE "agent" DROP CONSTRAINT "agent_workspaceId_fkey";

-- DropForeignKey
ALTER TABLE "ai_connection" DROP CONSTRAINT "ai_connection_providerId_fkey";

-- DropForeignKey
ALTER TABLE "execution" DROP CONSTRAINT "execution_agentId_fkey";

-- DropForeignKey
ALTER TABLE "execution" DROP CONSTRAINT "execution_workspaceId_fkey";

-- DropForeignKey
ALTER TABLE "execution_log" DROP CONSTRAINT "execution_log_executionId_fkey";

-- AlterTable
ALTER TABLE "agent" DROP CONSTRAINT "agent_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
DROP COLUMN "roleId",
ADD COLUMN     "roleId" INTEGER NOT NULL,
DROP COLUMN "permissionProfileId",
ADD COLUMN     "permissionProfileId" INTEGER NOT NULL,
DROP COLUMN "providerId",
ADD COLUMN     "providerId" INTEGER NOT NULL,
DROP COLUMN "connectionId",
ADD COLUMN     "connectionId" INTEGER,
DROP COLUMN "workspaceId",
ADD COLUMN     "workspaceId" INTEGER,
ADD CONSTRAINT "agent_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "agent_role" DROP CONSTRAINT "agent_role_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
ADD CONSTRAINT "agent_role_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "ai_connection" DROP CONSTRAINT "ai_connection_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
DROP COLUMN "providerId",
ADD COLUMN     "providerId" INTEGER NOT NULL,
ADD CONSTRAINT "ai_connection_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "ai_provider" DROP CONSTRAINT "ai_provider_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
ADD CONSTRAINT "ai_provider_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "execution" DROP CONSTRAINT "execution_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
DROP COLUMN "agentId",
ADD COLUMN     "agentId" INTEGER NOT NULL,
DROP COLUMN "workspaceId",
ADD COLUMN     "workspaceId" INTEGER NOT NULL,
ADD CONSTRAINT "execution_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "execution_log" DROP CONSTRAINT "execution_log_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
DROP COLUMN "executionId",
ADD COLUMN     "executionId" INTEGER NOT NULL,
ADD CONSTRAINT "execution_log_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "permission_profile" DROP CONSTRAINT "permission_profile_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
ADD CONSTRAINT "permission_profile_pkey" PRIMARY KEY ("id");

-- AlterTable
ALTER TABLE "workspace" DROP CONSTRAINT "workspace_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" SERIAL NOT NULL,
ADD CONSTRAINT "workspace_pkey" PRIMARY KEY ("id");

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
