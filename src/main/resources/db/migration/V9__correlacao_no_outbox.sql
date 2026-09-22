-- Id de correlacao viajando com o evento.
--
-- O pedido pago atravessa quatro processos — requisicao HTTP, webhook, worker do
-- outbox e consumidores —, cada um numa thread e num momento diferentes. O MDC
-- do SLF4J e por thread e nao cruza essas fronteiras sozinho: o id precisa
-- viajar junto com o evento, gravado aqui e depois copiado para o cabecalho da
-- mensagem.
--
-- Sem isso, investigar "o que aconteceu com o pedido 4711" exige cruzar
-- horarios entre quatro trechos de log na mao.

ALTER TABLE outbox ADD COLUMN correlacao_id VARCHAR(64);
