# Use an official Python runtime as a parent image
FROM python:3.9-slim

# Set working directory
WORKDIR /usr/src/app

# Install system dependencies (needed for compiling some Python libraries)
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# Copy the current directory contents into the container
COPY . .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Install the application itself
RUN pip install .

# Setup default settings (OpenTapioca requires a settings.py file)
#RUN cp settings_template.py settings.py (You don't need this anymore because your COPY . . command already brings in your new, custom settings.py.)

# Expose the port (OpenTapioca's app.py usually defaults to 5000 or 80)
# We will override the launch command to ensure it listens on port 80
EXPOSE 80

# Run the application using Gunicorn for stability, binding to port 80
# If gunicorn is not in requirements, we fall back to python app.py
RUN pip install gunicorn
CMD ["gunicorn", "-w", "4", "-b", "0.0.0.0:80", "app:app"]