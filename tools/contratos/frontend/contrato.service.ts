import { apiClient } from "./apiClient";
import type { DadosContrato } from "@/types";

export interface ContratoGeradoMetadados {
  id: number;
  emprestimoId: number;
  versaoTemplate: string;
  dataGeracao: string;
  nomeArquivo: string;
  hashDocumento: string;
}

const rota = (clienteId: number, emprestimoId: number) =>
  `/clientes/${clienteId}/emprestimos/${emprestimoId}`;

export const contratoService = {
  getDadosContrato: (clienteId: number, emprestimoId: number) =>
    apiClient.get<DadosContrato>(`${rota(clienteId, emprestimoId)}/dados-contrato`),
  gerar: (clienteId: number, emprestimoId: number) =>
    apiClient.post<ContratoGeradoMetadados>(`${rota(clienteId, emprestimoId)}/contratos`, undefined,
      { body: undefined }),
  listar: (clienteId: number, emprestimoId: number) =>
    apiClient.get<ContratoGeradoMetadados[]>(`${rota(clienteId, emprestimoId)}/contratos`),
  baixar: (clienteId: number, emprestimoId: number, contratoId: number) =>
    apiClient.getBlob(`${rota(clienteId, emprestimoId)}/contratos/${contratoId}/download`),
};
