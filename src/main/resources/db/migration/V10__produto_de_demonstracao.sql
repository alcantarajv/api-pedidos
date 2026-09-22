-- Produto usado pela pagina inicial para demonstrar a idempotencia ao vivo.
--
-- Dado de demonstracao numa migration e discutivel, e a alternativa aqui era
-- pior: sem nenhum produto, a pagina inicial do deploy publico mostraria um erro
-- para quem abre o link do portfolio. A insercao e condicional, entao rodar de
-- novo nao duplica nada.
--
-- O estoque e alto de proposito: cada execucao da demonstracao reserva uma
-- unidade. A demonstracao cancela o pedido no fim, o que devolve o estoque, e a
-- expiracao automatica recolhe o que sobrar — mas a folga evita que uma sequencia
-- de visitas esgote o produto e quebre a pagina.

INSERT INTO produtos (nome, descricao, preco, estoque_disponivel, estoque_reservado, ativo)
SELECT 'Teclado Mecanico ABNT2',
       'Produto de demonstracao usado pela pagina inicial desta API.',
       349.90, 100000, 0, TRUE
WHERE NOT EXISTS (SELECT 1 FROM produtos WHERE LOWER(nome) = LOWER('Teclado Mecanico ABNT2'));
