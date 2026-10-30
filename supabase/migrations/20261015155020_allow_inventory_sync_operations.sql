alter table public.finance_operations
	drop constraint if exists finance_operations_type_check;

alter table public.finance_operations
	add constraint finance_operations_type_check check (operation_type in (
		'SALE_POSTED', 'SALE_VOIDED', 'EXPENSE_POSTED', 'EXPENSE_VOIDED',
		'RECEIVABLE_POSTED', 'RECEIVABLE_VOIDED', 'RECEIVABLE_PAYMENT',
		'PAYABLE_POSTED', 'PAYABLE_VOIDED', 'PAYABLE_PAYMENT',
		'SHIPMENT_LINE_ADDED', 'SHIPMENT_RECEIVED',
		'INVENTORY_ITEM_POSTED', 'INVENTORY_ITEM_DELETED'
	));

create or replace function public.apply_finance_operation(
	p_operation_id uuid,
	p_operation_type text,
	p_aggregate_id text,
	p_payload_version integer,
	p_payload jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = pg_catalog, public
as $$
declare
	authenticated_owner uuid := auth.uid();
	inserted_rows integer;
	existing_operation public.finance_operations%rowtype;
begin
	if authenticated_owner is null then
		raise insufficient_privilege using message = 'authentication_required';
	end if;
	if p_operation_id is null or p_operation_type is null or p_aggregate_id is null
			or p_aggregate_id = '' or p_payload_version is null or p_payload_version < 1
			or p_payload is null or jsonb_typeof(p_payload) <> 'object' then
		raise check_violation using message = 'invalid_operation';
	end if;
	if p_operation_type not in (
		'SALE_POSTED', 'SALE_VOIDED', 'EXPENSE_POSTED', 'EXPENSE_VOIDED',
		'RECEIVABLE_POSTED', 'RECEIVABLE_VOIDED', 'RECEIVABLE_PAYMENT',
		'PAYABLE_POSTED', 'PAYABLE_VOIDED', 'PAYABLE_PAYMENT',
		'SHIPMENT_LINE_ADDED', 'SHIPMENT_RECEIVED',
		'INVENTORY_ITEM_POSTED', 'INVENTORY_ITEM_DELETED'
	) then
		raise check_violation using message = 'unsupported_operation_type';
	end if;

	insert into public.finance_operations (
		owner_id, operation_id, operation_type, aggregate_id, payload_version, payload
	) values (
		authenticated_owner, p_operation_id, p_operation_type, p_aggregate_id,
		p_payload_version, p_payload
	) on conflict (owner_id, operation_id) do nothing;
	get diagnostics inserted_rows = row_count;

	if inserted_rows = 1 then
		return jsonb_build_object('accepted', true, 'duplicate', false);
	end if;

	select * into existing_operation
	from public.finance_operations
	where owner_id = authenticated_owner and operation_id = p_operation_id;

	if existing_operation.operation_type is distinct from p_operation_type
			or existing_operation.aggregate_id is distinct from p_aggregate_id
			or existing_operation.payload_version is distinct from p_payload_version
			or existing_operation.payload is distinct from p_payload then
		raise unique_violation using message = 'operation_id_reused_with_different_payload';
	end if;

	return jsonb_build_object('accepted', true, 'duplicate', true);
end;
$$;

revoke all on function public.apply_finance_operation(uuid, text, text, integer, jsonb)
	from public, anon, authenticated;
grant execute on function public.apply_finance_operation(uuid, text, text, integer, jsonb)
	to authenticated;
