-- Demo users for the lecture. Password for all of them: demo1234 (demo only!)
insert into app_user (username, password_hash, display_name) values
    ('alice', '{bcrypt}$2a$10$MISBCItjlG6EgXEavA7GM.60ByKlOapnnv1NdBIbbSFuwqo5i2QpO', 'Alice'),
    ('bob',   '{bcrypt}$2a$10$ZviuG5PeJ2U4LCOTfE.fC.g/4eU34x0YMNHy84tawXbVjWYsvGQ4.', 'Bob'),
    ('carol', '{bcrypt}$2a$10$1kCFp9rJaqmxfUvPX5qN3ONqQAb9MPkSGKluO2sXnavWlMRecFBa6', 'Carol');

insert into todo_list (title, visibility, owner_id)
select 'Lecture prep', 'PUBLIC', id from app_user where username = 'alice';

insert into todo_list (title, visibility, owner_id)
select 'Alice''s personal', 'PRIVATE', id from app_user where username = 'alice';

insert into todo_item (list_id, title, done)
select l.id, i.title, i.done
from todo_list l
cross join (values ('Book the lecture hall', true),
                   ('Prepare slides', false),
                   ('Rehearse the demo', false)) as i(title, done)
where l.title = 'Lecture prep';

insert into todo_item (list_id, title)
select id, 'Buy coffee' from todo_list where title = 'Alice''s personal';
