UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid IN (
'cc2bce18-3cae-4f58-83ed-2b2a801f98de',
'ba9cf04d-5aaa-43fa-a5c2-1e7a886548d5',
'dee590e9-3a60-498a-827a-934e95ae2624',
'b2f1d602-8187-4a60-8f7a-31be51410adf',
'07936da4-829f-4568-aacf-79cee375d7e6',
'3d1b6d67-bee7-4b99-81cb-290e1c588bf8',
'c78a4a8e-1b5d-4b89-b4c2-ca4f6bd88014',
'6c808d86-aee7-41b3-b233-262f1efc73de',
'1a221eeb-ef2f-4cfa-81a4-97876d83c7ed',
'7e37ab19-985b-44b4-9c15-d7bd1873f73b',
'77f7e442-daa1-40ac-b765-17e7e9f31479',
'd0479ef8-4bb9-441b-a43b-fceed4311b27',
'6e8b82ae-9be8-4b83-922f-9ce882039484',
'bcd6cd7e-2746-4fe0-98ce-6e5652cbde34',
'f2929ab7-1aff-4aee-9a36-923eba5d44c3',
'09e7f66c-f78d-4d1d-ba46-2fbe8fda044a',
'bc8ee1a0-db71-4d97-9ab9-5ae6c5004f75',
'0a10d836-7a67-4695-94b9-c14cd88f67be',
'6244fc6f-9899-4ee1-ad66-a2ee21d624ef',
'f95625e7-9a61-4853-bb2b-2bf79c157a6d',
'017dcc19-8c5d-4edb-8568-e65f2583d827'
);
