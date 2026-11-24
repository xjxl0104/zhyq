-- A WeChat account may own an application and be a verified salesperson for another warehouse.
-- The unique key continues to prevent duplicate owners and duplicate member bindings for one WeChat.
ALTER TABLE crm_warehouse_contact
    DROP INDEX uk_wh_contact_openid,
    ADD UNIQUE KEY uk_wh_contact_openid_role (openid,role);
