-- Indices para a rotina de limpeza da Etapa 10.
--
-- Sem eles, apagar registros antigos varreria a tabela inteira — e sao
-- justamente as tabelas que mais crescem, porque ganham uma linha por evento
-- recebido e por mensagem consumida.
--
-- A tabela eventos_processados ja ganhou o seu indice na V5.

CREATE INDEX mensagens_consumidas_por_data ON mensagens_consumidas (consumida_em);

-- Parcial: a limpeza so remove o que ja foi publicado. Um evento pendente
-- antigo e problema para investigar, nao lixo para apagar.
CREATE INDEX outbox_publicados ON outbox (publicado_em) WHERE publicado_em IS NOT NULL;
