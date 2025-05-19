# start.sh
#!/bin/bash
(cd backend && source venv/bin/activate && uvicorn main:app --reload) &
(cd frontend && ng serve)
