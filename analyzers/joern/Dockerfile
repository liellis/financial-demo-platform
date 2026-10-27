FROM ghcr.io/joernio/joern@sha256:11b574bc88e8fdf1d7f324cfd9c8e7b696495f5e50a48b633251fa672156a38a
USER root
COPY service.py export.sc /runner/
WORKDIR /runner
CMD ["python3", "/runner/service.py"]
