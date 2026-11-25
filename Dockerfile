FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal

ADD https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-x86_64.rpm /tmp/emulator.rpm
RUN rpm -i /tmp/emulator.rpm \
    && rm /tmp/emulator.rpm \
    && mkdir -p /home/emulator \
    && chown 1000 /home/emulator

USER 1000
ENV HOME=/home/emulator EMULATOR_BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["aws-emulator"]